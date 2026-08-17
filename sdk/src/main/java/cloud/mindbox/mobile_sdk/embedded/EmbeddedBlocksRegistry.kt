package cloud.mindbox.mobile_sdk.embedded

import android.os.Handler
import android.os.Looper
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.Closeable

/**
 * One registered block: how the registry talks back to a view.
 **/
internal interface EmbeddedBlockHandle {

    val isActive: Boolean

    fun onContentResolved(content: InAppType.Embedded?)
}

internal interface EmbeddedBlocksRegistry {

    fun register(placeSystemName: String, handle: EmbeddedBlockHandle): Closeable

    fun onBlockAppeared(placeSystemName: String)

    fun startListening()
}

internal class EmbeddedBlocksRegistryImpl(
    private val inAppInteractor: InAppInteractor,
    private val scopeProvider: () -> CoroutineScope,
) : EmbeddedBlocksRegistry {

    private val handlesByPlace = mutableMapOf<String, MutableList<EmbeddedBlockHandle>>()
    private val resolvingPlaces = mutableSetOf<String>()

    private val reResolveQueuedPlaces = mutableMapOf<String, InAppEventType?>()

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
            val content = try {
                inAppInteractor.selectInAppForPlace(
                    place,
                    triggerEvent ?: InAppEventType.EmbeddedPlaceRequested(place)
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                mindboxLogW("[EmbeddedBlock] Resolving place '$place' failed: $error")
                null
            }
            runOnMain { deliver(place, content) }
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
