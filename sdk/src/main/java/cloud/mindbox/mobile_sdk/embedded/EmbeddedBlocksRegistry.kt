package cloud.mindbox.mobile_sdk.embedded

import android.os.Handler
import android.os.Looper
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.EmbeddedResolveResult
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.Closeable

/**
 * One registered block: how the registry talks back to a view.
 **/
internal interface EmbeddedBlockHandle {

    val isActive: Boolean

    fun onContentResolved(content: InAppType.Embedded?)

    /**
     * A winner exists but its `delayTime` has not elapsed: the SDK has answered — the waiting
     * budget stands down and the delay leaves the show's `timeToDisplay` — while the block
     * keeps its loading skeleton until the delivery.
     */
    fun onContentPending() {}
}

internal interface EmbeddedBlocksRegistry {

    fun register(placeSystemName: String, handle: EmbeddedBlockHandle): Closeable

    fun onBlockAppeared(placeSystemName: String)

    fun startListening()
}

internal class EmbeddedBlocksRegistryImpl(
    private val inAppInteractor: InAppInteractor,
    // Read on every use, never captured: the SDK scope is recreated on a soft reinitialization.
    private val scopeProvider: () -> CoroutineScope = { Mindbox.mindboxScope },
) : EmbeddedBlocksRegistry {

    private val handlesByPlace = mutableMapOf<String, MutableList<EmbeddedBlockHandle>>()
    private val resolvingPlaces = mutableSetOf<String>()

    private val reResolveQueuedPlaces = mutableMapOf<String, InAppEventType?>()

    private class PendingDelay(val inAppId: String, val job: Job)

    private val delayJobsByPlace = mutableMapOf<String, PendingDelay>()

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
                        onPlaceEvent(placeEvent.placeSystemName.trim(), placeEvent.triggerEvent)
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

    override fun register(placeSystemName: String, handle: EmbeddedBlockHandle): Closeable {
        val place = placeSystemName.trim()
        runOnMain {
            restartChannelsIfDead()
            handlesByPlace.getOrPut(place) { mutableListOf() }.add(handle)
            mindboxLogI("[EmbeddedBlock] Block registered for place '$place'")
        }
        return Closeable {
            runOnMain {
                handlesByPlace[place]?.remove(handle)
                if (handlesByPlace[place]?.isEmpty() == true) {
                    handlesByPlace.remove(place)
                    reResolveQueuedPlaces.remove(place)
                }
                mindboxLogI("[EmbeddedBlock] Block unregistered from place '$place'")
            }
        }
    }

    override fun onBlockAppeared(placeSystemName: String) {
        val place = placeSystemName.trim()
        runOnMain {
            restartChannelsIfDead()
            resolvePlace(place)
        }
    }

    private fun onPlaceEvent(place: String, triggerEvent: InAppEventType) {
        val handles = handlesByPlace[place]
        if (handles.isNullOrEmpty()) {
            mindboxLogI("[EmbeddedBlock] Operation matched place '$place' but no block is registered, dropping")
            return
        }
        if (handles.none { handle -> handle.isActive }) {
            mindboxLogI("[EmbeddedBlock] Operation matched paused place '$place', nowhere to display — skipping")
            return
        }
        resolvePlace(place, triggerEvent)
    }

    private fun resolvePlace(place: String, triggerEvent: InAppEventType? = null) {
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
        handlesByPlace.forEach { (place, handles) ->
            if (handles.any { handle -> handle.isActive }) {
                mindboxLogI("[EmbeddedBlock] Re-resolving place '$place' ($reason)")
                resolvePlace(place)
            } else {
                mindboxLogI("[EmbeddedBlock] Place '$place' is paused, nowhere to display — skipping ($reason)")
            }
        }
    }

    private fun handleResolved(place: String, result: EmbeddedResolveResult?) {
        val delayTime = result?.delayTime?.takeIf { delay -> delay.interval > 0 }
        if (result == null || delayTime == null) {
            delayJobsByPlace.remove(place)?.job?.cancel()
            deliver(place, result?.variant)
            return
        }
        val running = delayJobsByPlace[place]
        if (running != null && running.job.isActive && running.inAppId == result.variant.inAppId) {
            mindboxLogI(
                "[EmbeddedBlock] Winner ${result.variant.inAppId} for place '$place' is already " +
                    "waiting out its delay, keeping the running timer"
            )
            notifyPending(place)
            return
        }
        running?.job?.cancel()
        mindboxLogI(
            "[EmbeddedBlock] Winner ${result.variant.inAppId} for place '$place' waits its " +
                "delayTime of ${delayTime.interval} ms before the delivery"
        )
        notifyPending(place)
        val job = scopeProvider().launch {
            delay(delayTime.interval)
            val self = coroutineContext[Job]
            runOnMain {
                if (delayJobsByPlace[place]?.job !== self) return@runOnMain
                delayJobsByPlace.remove(place)
                inAppInteractor.markEmbeddedDelayWaitedOut(place, result.variant.inAppId)
                deliver(place, result.variant)
            }
        }
        delayJobsByPlace[place] = PendingDelay(result.variant.inAppId, job)
    }

    private fun handleResolveFailure(place: String) {
        val running = delayJobsByPlace[place]
        if (running != null && running.job.isActive) {
            mindboxLogI(
                "[EmbeddedBlock] Resolving place '$place' failed while winner ${running.inAppId} " +
                    "waits out its delay, keeping the running timer"
            )
            notifyPending(place)
            return
        }
        deliver(place, null)
    }

    private fun notifyPending(place: String) {
        handlesByPlace[place]?.toList()?.forEach { handle ->
            loggingRunCatching { handle.onContentPending() }
        }
    }

    private fun deliver(place: String, content: InAppType.Embedded?) {
        val handles = handlesByPlace[place]
        if (handles.isNullOrEmpty()) {
            mindboxLogW("[EmbeddedBlock] No block is registered for place '$place', dropping the content")
            return
        }
        handles.toList().forEach { handle ->
            loggingRunCatching { handle.onContentResolved(content) }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }
}
