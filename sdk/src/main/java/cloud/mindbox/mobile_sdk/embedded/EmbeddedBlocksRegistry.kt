package cloud.mindbox.mobile_sdk.embedded

import android.os.Handler
import android.os.Looper
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.EmbeddedResolveOutcome
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.PlaceKey
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.Closeable
import java.lang.ref.WeakReference

/**
 * One registered block: how the registry talks back to a view.
 **/
internal interface EmbeddedBlockHandle {

    val isActive: Boolean

    val isHoldingContent: Boolean

    fun onContentResolved(content: InAppType.Embedded?)

    fun onContentPending() {}

    fun onConfigUnavailable()
}

internal interface EmbeddedBlocksRegistry {

    fun register(placeSystemName: PlaceKey, handle: EmbeddedBlockHandle): Closeable

    fun onBlockAppeared(placeSystemName: PlaceKey)

    fun onBlockContentDropped(placeSystemName: PlaceKey)

    fun startListening()
}

internal class EmbeddedBlocksRegistryImpl(
    private val inAppInteractor: InAppInteractor,
    // Read on every use, never captured: the SDK scope is recreated on a soft reinitialization.
    private val scopeProvider: () -> CoroutineScope = { Mindbox.mindboxScope },
) : EmbeddedBlocksRegistry {

    // Weak on purpose: a host with no lifecycle owner to say goodbye — a plain Dialog, a
    // PopupWindow, an app that swaps views itself — only lets its block go, and a strong entry in
    // this process-wide map would keep the block, its view and the Activity behind it alive.
    private val handlesByPlace = mutableMapOf<PlaceKey, MutableList<WeakReference<EmbeddedBlockHandle>>>()
    private val resolvingPlaces = mutableSetOf<PlaceKey>()

    private val reResolveQueuedPlaces = mutableMapOf<PlaceKey, InAppEventType?>()

    private class PendingDelay(val inAppId: String, val job: Job)

    private val delayJobsByPlace = mutableMapOf<PlaceKey, PendingDelay>()

    private val mainHandler = Handler(Looper.getMainLooper())

    private var channelJobs: List<Job> = emptyList()

    override fun startListening() {
        runOnMain { restartChannelsIfDead() }
    }

    private fun restartChannelsIfDead() {
        val isFirstStart = channelJobs.isEmpty()
        if (!isFirstStart && channelJobs.all { job -> job.isActive }) return
        channelJobs.forEach { job -> job.cancel() }

        val scope = scopeProvider()
        channelJobs = listOf(
            scope.launch {
                inAppInteractor.listenConfigUpdates().collect {
                    runOnMain { invalidateAll(reason = "config update") }
                }
            },
            scope.launch {
                inAppInteractor.listenEmbeddedPlaceEvents().collect { placeEvent ->
                    runOnMain {
                        onPlaceEvent(placeEvent.placeSystemName, placeEvent.triggerEvent)
                    }
                }
            },
        )
        if (isFirstStart) return

        mindboxLogI(
            "[EmbeddedBlock] Invalidation channels died with the previous SDK scope, resubscribed"
        )
        invalidateAll(reason = "channels resubscribed")
    }

    override fun register(placeSystemName: PlaceKey, handle: EmbeddedBlockHandle): Closeable {
        runOnMain {
            restartChannelsIfDead()
            handlesByPlace.getOrPut(placeSystemName) { mutableListOf() }.add(WeakReference(handle))
            mindboxLogI("[EmbeddedBlock] Block registered for place '$placeSystemName'")
        }
        return Closeable {
            runOnMain {
                handlesByPlace[placeSystemName]?.removeAll { reference ->
                    reference.get().let { registered -> registered === handle || registered == null }
                }
                forgetPlaceIfEmpty(placeSystemName)
                mindboxLogI("[EmbeddedBlock] Block unregistered from place '$placeSystemName'")
            }
        }
    }

    override fun onBlockAppeared(placeSystemName: PlaceKey) {
        runOnMain {
            restartChannelsIfDead()
            resolvePlace(placeSystemName)
        }
    }

    private fun liveHandles(place: PlaceKey): List<EmbeddedBlockHandle> {
        val references = handlesByPlace[place] ?: return emptyList()
        val handles = references.mapNotNull { reference -> reference.get() }
        if (handles.size != references.size) {
            references.removeAll { reference -> reference.get() == null }
            forgetPlaceIfEmpty(place)
        }
        return handles
    }

    override fun onBlockContentDropped(placeSystemName: PlaceKey) {
        runOnMain {
            if (liveHandles(placeSystemName).none { handle -> handle.isHoldingContent }) {
                inAppInteractor.releasePlaceShow(placeSystemName)
            }
        }
    }

    private fun forgetPlaceIfEmpty(place: PlaceKey) {
        if (handlesByPlace[place]?.isEmpty() != true) return
        handlesByPlace.remove(place)
        reResolveQueuedPlaces.remove(place)
        inAppInteractor.releasePlaceShow(place)
    }

    private fun onPlaceEvent(place: PlaceKey, triggerEvent: InAppEventType) {
        val handles = liveHandles(place)
        if (handles.isEmpty()) {
            mindboxLogI("[EmbeddedBlock] Operation matched place '$place' but no block is registered, dropping")
            return
        }
        if (handles.none { handle -> handle.isActive }) {
            mindboxLogI("[EmbeddedBlock] Operation matched paused place '$place', nowhere to display — skipping")
            return
        }
        resolvePlace(place, triggerEvent)
    }

    private fun resolvePlace(place: PlaceKey, triggerEvent: InAppEventType? = null) {
        if (!resolvingPlaces.add(place)) {
            mindboxLogI(
                "[EmbeddedBlock] Place '$place' is already resolving, queueing one more pass"
            )
            reResolveQueuedPlaces[place] = triggerEvent ?: reResolveQueuedPlaces[place]
            return
        }
        val job = scopeProvider().launch {
            val resolved = try {
                Result.success(
                    inAppInteractor.selectInAppForPlace(
                        place,
                        triggerEvent ?: InAppEventType.EmbeddedPlaceRequested(place)
                    )
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                mindboxLogW("[EmbeddedBlock] Resolving place '$place' failed: $error")
                Result.failure(error)
            }
            runOnMain {
                resolved.fold(
                    onSuccess = { result -> handleResolved(place, result) },
                    onFailure = { handleResolveFailure(place) },
                )
            }
        }
        job.invokeOnCompletion {
            runOnMain {
                resolvingPlaces.remove(place)
                if (reResolveQueuedPlaces.containsKey(place)) {
                    val queuedTrigger = reResolveQueuedPlaces.remove(place)
                    mindboxLogI("[EmbeddedBlock] Re-running queued resolve for place '$place'")
                    resolvePlace(place, queuedTrigger)
                }
            }
        }
    }

    private fun invalidateAll(reason: String) {
        handlesByPlace.keys.toList().forEach { place ->
            val handles = liveHandles(place)
            when {
                handles.isEmpty() -> Unit
                handles.any { handle -> handle.isActive } -> {
                    mindboxLogI("[EmbeddedBlock] Re-resolving place '$place' ($reason)")
                    resolvePlace(place)
                }
                else ->
                    mindboxLogI("[EmbeddedBlock] Place '$place' is paused, nowhere to display — skipping ($reason)")
            }
        }
    }

    private fun handleResolved(place: PlaceKey, outcome: EmbeddedResolveOutcome) {
        when (outcome) {
            is EmbeddedResolveOutcome.Content -> handleContent(place, outcome)
            EmbeddedResolveOutcome.Empty -> deliverNow(place, null)
            EmbeddedResolveOutcome.ConfigUnavailable -> handleConfigUnavailable(place)
        }
    }

    private fun handleContent(place: PlaceKey, outcome: EmbeddedResolveOutcome.Content) {
        val winner = outcome.variant
        val delayTime = outcome.delayTime?.takeIf { delay -> delay.interval > 0 }
        if (delayTime == null) {
            deliverNow(place, winner)
            return
        }
        val running = delayJobsByPlace[place]
        if (running != null && running.job.isActive && running.inAppId == winner.inAppId) {
            mindboxLogI(
                "[EmbeddedBlock] Winner ${winner.inAppId} for place '$place' is already " +
                    "waiting out its delay, keeping the running timer"
            )
            notifyPending(place)
            return
        }
        running?.job?.cancel()
        mindboxLogI(
            "[EmbeddedBlock] Winner ${winner.inAppId} for place '$place' waits its " +
                "delayTime of ${delayTime.interval} ms before the delivery"
        )
        notifyPending(place)
        val job = scopeProvider().launch {
            delay(delayTime.interval)
            val self = coroutineContext[Job]
            runOnMain {
                if (delayJobsByPlace[place]?.job !== self) return@runOnMain
                delayJobsByPlace.remove(place)
                inAppInteractor.markEmbeddedDelayWaitedOut(place, winner.inAppId)
                deliver(place, winner)
            }
        }
        delayJobsByPlace[place] = PendingDelay(winner.inAppId, job)
    }

    private fun handleResolveFailure(place: PlaceKey) {
        if (keepsRunningDelay(place, "Resolving place '$place' failed")) return
        deliver(place, null)
    }

    private fun handleConfigUnavailable(place: PlaceKey) {
        if (keepsRunningDelay(place, "The SDK has no config to answer for place '$place'")) return
        deliverConfigUnavailable(place)
    }

    private fun keepsRunningDelay(place: PlaceKey, what: String): Boolean {
        val running = delayJobsByPlace[place]?.takeIf { pending -> pending.job.isActive } ?: return false
        mindboxLogI("[EmbeddedBlock] $what while winner ${running.inAppId} waits out its delay, keeping the running timer")
        notifyPending(place)
        return true
    }

    private fun deliverNow(place: PlaceKey, content: InAppType.Embedded?) {
        delayJobsByPlace.remove(place)?.job?.cancel()
        deliver(place, content)
    }

    private fun deliverConfigUnavailable(place: PlaceKey) {
        inAppInteractor.releasePlaceShow(place)
        val handles = liveHandles(place)
        if (handles.isEmpty()) {
            mindboxLogW("[EmbeddedBlock] No block is registered for place '$place', dropping the answer")
            return
        }
        mindboxLogW("[EmbeddedBlock] The SDK has no config to answer for place '$place', the blocks report a failure")
        handles.forEach { handle ->
            loggingRunCatching { handle.onConfigUnavailable() }
        }
    }

    private fun notifyPending(place: PlaceKey) {
        liveHandles(place).forEach { handle ->
            loggingRunCatching { handle.onContentPending() }
        }
    }

    private fun deliver(place: PlaceKey, content: InAppType.Embedded?) {
        val handles = liveHandles(place)
        if (handles.isEmpty()) {
            mindboxLogW("[EmbeddedBlock] No block is registered for place '$place', dropping the content")
            inAppInteractor.releasePlaceShow(place)
            return
        }
        // The hold is taken here, where the content really goes to the blocks — after any delay,
        // not at the resolve — so the check and the spend are one step against the overlay.
        val delivered = content?.takeIf { winner ->
            inAppInteractor.reservePlaceShow(place, winner).also { reserved ->
                if (!reserved) {
                    mindboxLogI(
                        "[EmbeddedBlock] In-app ${winner.inAppId} won place '$place' but the show " +
                            "budgets are spent, the place stays empty"
                    )
                }
            }
        }
        if (delivered == null) inAppInteractor.releasePlaceShow(place)
        handles.forEach { handle ->
            loggingRunCatching { handle.onContentResolved(delivered) }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }
}
