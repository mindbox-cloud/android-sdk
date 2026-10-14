package cloud.mindbox.mobile_sdk.embedded

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.EmbeddedResolveOutcome
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.PlaceKey
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.Closeable
import java.lang.ref.WeakReference

internal data class EmbeddedPlaceAnswer(
    val isByOperation: Boolean,
    val selectionTime: Milliseconds,
)

internal val InAppType.Embedded.pageLayer: Layer.WebViewLayer?
    get() = layers.filterIsInstance<Layer.WebViewLayer>().firstOrNull()

/**
 * One registered block: how the registry talks back to a view.
 **/
internal interface EmbeddedBlockHandle {

    val isActive: Boolean

    val isPausedForBackground: Boolean

    val isHoldingContent: Boolean

    @MainThread
    fun onContentResolved(content: InAppType.Embedded?, answer: EmbeddedPlaceAnswer)

    fun onContentPending() {}

    @MainThread
    fun onConfigUnavailable(answer: EmbeddedPlaceAnswer)

    @MainThread
    fun onAppResumedOn(activity: Activity)
}

internal interface EmbeddedBlocksRegistry {

    fun register(placeSystemName: PlaceKey, handle: EmbeddedBlockHandle): Closeable

    fun onBlockAppeared(placeSystemName: PlaceKey)

    fun onBlockContentDropped(placeSystemName: PlaceKey)

    fun onAppResumedOn(activity: Activity)

    fun startListening()
}

internal class EmbeddedBlocksRegistryImpl(
    private val inAppInteractor: InAppInteractor,
    // Read on every use, never captured: the SDK scope is recreated on a soft reinitialization.
    private val scopeProvider: () -> CoroutineScope = { Mindbox.mindboxScope },
    private val monotonicNow: () -> Milliseconds,
) : EmbeddedBlocksRegistry {

    // Weak on purpose: a host with no lifecycle owner to say goodbye — a plain Dialog, a
    // PopupWindow, an app that swaps views itself — only lets its block go, and a strong entry in
    // this process-wide map would keep the block, its view and the Activity behind it alive.
    private val handlesByPlace = mutableMapOf<PlaceKey, MutableList<WeakReference<EmbeddedBlockHandle>>>()
    private val resolvingPlaces = mutableSetOf<PlaceKey>()

    private class ResolvePass(
        val trigger: InAppEventType?,
        val includesNonOperation: Boolean = trigger == null,
        val includesAppearance: Boolean = false,
    ) {
        fun mergedWith(newer: ResolvePass): ResolvePass = ResolvePass(
            trigger = newer.trigger ?: trigger,
            includesNonOperation = includesNonOperation || newer.includesNonOperation,
            includesAppearance = includesAppearance || newer.includesAppearance,
        )
    }

    private val reResolveQueuedPlaces = mutableMapOf<PlaceKey, ResolvePass>()

    private val placesAppearedMidResolve = mutableSetOf<PlaceKey>()

    private class PendingDelay(
        val job: Job,
        var winner: InAppType.Embedded,
        val answer: EmbeddedPlaceAnswer,
    ) {
        val isPending: Boolean get() = !job.isCancelled
    }

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
            resolvePlace(placeSystemName, ResolvePass(trigger = null, includesAppearance = true))
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

    override fun onAppResumedOn(activity: Activity) {
        runOnMain {
            forEachLiveHandle { handle -> handle.onAppResumedOn(activity) }
        }
    }

    private fun forEachLiveHandle(action: (EmbeddedBlockHandle) -> Unit) {
        handlesByPlace.keys.toList().forEach { place ->
            liveHandles(place).forEach { handle -> loggingRunCatching { action(handle) } }
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
        resolvePlace(place, ResolvePass(trigger = triggerEvent))
    }

    private fun resolvePlace(place: PlaceKey, pass: ResolvePass = ResolvePass(trigger = null)) {
        if (!resolvingPlaces.add(place)) {
            mindboxLogI(
                "[EmbeddedBlock] Place '$place' is already resolving, queueing one more pass"
            )
            if (pass.includesAppearance) placesAppearedMidResolve.add(place)
            reResolveQueuedPlaces.merge(place, pass)
            return
        }
        val triggerEvent = pass.trigger
        val isByOperation = triggerEvent != null && !pass.includesNonOperation
        val requestedAt = monotonicNow()
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
            val selectionTime = Milliseconds(monotonicNow().interval - requestedAt.interval)
            runOnMain {
                val hasAppearedMidResolve = placesAppearedMidResolve.remove(place)
                val delivered = EmbeddedPlaceAnswer(
                    isByOperation = isByOperation && !hasAppearedMidResolve,
                    selectionTime = selectionTime,
                )
                resolved.fold(
                    onSuccess = { result -> handleResolved(place, result, delivered) },
                    onFailure = { handleResolveFailure(place, delivered) },
                )
            }
        }
        job.invokeOnCompletion {
            runOnMain {
                resolvingPlaces.remove(place)
                placesAppearedMidResolve.remove(place)
                reResolveQueuedPlaces.remove(place)?.let { queued ->
                    mindboxLogI("[EmbeddedBlock] Re-running queued resolve for place '$place'")
                    resolvePlace(place, queued)
                }
            }
        }
    }

    private fun MutableMap<PlaceKey, ResolvePass>.merge(place: PlaceKey, pass: ResolvePass) {
        this[place] = this[place]?.mergedWith(pass) ?: pass
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

    private fun handleResolved(place: PlaceKey, outcome: EmbeddedResolveOutcome, answer: EmbeddedPlaceAnswer) {
        when (outcome) {
            is EmbeddedResolveOutcome.Content -> handleContent(place, outcome, answer)
            EmbeddedResolveOutcome.Empty -> deliverNow(place, null, answer)
            EmbeddedResolveOutcome.ConfigUnavailable -> handleConfigUnavailable(place, answer)
        }
    }

    private fun handleContent(place: PlaceKey, outcome: EmbeddedResolveOutcome.Content, answer: EmbeddedPlaceAnswer) {
        val winner = outcome.variant
        val delayTime = outcome.delayTime?.takeIf { delay -> delay.interval > 0 }
        if (delayTime == null) {
            deliverNow(place, winner, answer)
            return
        }
        val running = delayJobsByPlace[place]
        if (running != null && running.isPending && running.winner.inAppId == winner.inAppId) {
            mindboxLogI(
                "[EmbeddedBlock] Winner ${winner.inAppId} for place '$place' is already " +
                    "waiting out its delay, keeping the running timer and its selection time for the fresh content"
            )
            running.winner = winner
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
                val due = delayJobsByPlace[place]?.takeIf { pending -> pending.job === self } ?: return@runOnMain
                delayJobsByPlace.remove(place)
                inAppInteractor.markEmbeddedDelayWaitedOut(place, due.winner.inAppId)
                deliver(place, due.winner, due.answer)
            }
        }
        delayJobsByPlace[place] = PendingDelay(job, winner, answer)
    }

    private fun handleResolveFailure(place: PlaceKey, answer: EmbeddedPlaceAnswer) {
        if (keepsRunningDelay(place, "Resolving place '$place' failed")) return
        deliver(place, null, answer)
    }

    private fun handleConfigUnavailable(place: PlaceKey, answer: EmbeddedPlaceAnswer) {
        if (keepsRunningDelay(place, "The SDK has no config to answer for place '$place'")) return
        deliverConfigUnavailable(place, answer)
    }

    private fun keepsRunningDelay(place: PlaceKey, what: String): Boolean {
        val running = delayJobsByPlace[place]?.takeIf { pending -> pending.isPending } ?: return false
        mindboxLogI("[EmbeddedBlock] $what while winner ${running.winner.inAppId} waits out its delay, keeping the running timer")
        notifyPending(place)
        return true
    }

    private fun deliverNow(place: PlaceKey, content: InAppType.Embedded?, answer: EmbeddedPlaceAnswer) {
        delayJobsByPlace.remove(place)?.job?.cancel()
        deliver(place, content, answer)
    }

    private fun deliverConfigUnavailable(place: PlaceKey, answer: EmbeddedPlaceAnswer) {
        inAppInteractor.releasePlaceShow(place)
        val handles = liveHandles(place)
        if (handles.isEmpty()) {
            mindboxLogW("[EmbeddedBlock] No block is registered for place '$place', dropping the answer")
            return
        }
        mindboxLogW("[EmbeddedBlock] The SDK has no config to answer for place '$place', telling its blocks")
        handles.forEach { handle ->
            loggingRunCatching { handle.onConfigUnavailable(answer) }
        }
    }

    private fun notifyPending(place: PlaceKey) {
        liveHandles(place).forEach { handle ->
            loggingRunCatching { handle.onContentPending() }
        }
    }

    private fun deliver(place: PlaceKey, content: InAppType.Embedded?, answer: EmbeddedPlaceAnswer) {
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
                            "budgets are spent, the place has nothing to show"
                    )
                }
            }
        }
        if (delivered?.pageLayer == null) inAppInteractor.releasePlaceShow(place)
        handles.forEach { handle ->
            loggingRunCatching { handle.onContentResolved(delivered, answer) }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }
}
