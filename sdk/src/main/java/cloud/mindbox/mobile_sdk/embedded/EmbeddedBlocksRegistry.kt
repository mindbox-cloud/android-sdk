package cloud.mindbox.mobile_sdk.embedded

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.EmbeddedResolveOutcome
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.PlaceShowReservation
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.managers.LifecycleManager
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
    val sessionEpoch: Long,
    val isByOperation: Boolean,
    val selectionTime: Milliseconds,
    val isShowReserved: Boolean = true,
)

internal val InAppType.Embedded.pageLayer: Layer.WebViewLayer?
    get() = layers.filterIsInstance<Layer.WebViewLayer>().firstOrNull()

/**
 * One registered block: how the registry talks back to a view.
 **/
internal interface EmbeddedBlockHandle {

    val isActive: Boolean

    val isPausedForBackground: Boolean

    val isLeftBehind: Boolean

    val isHoldingContent: Boolean

    @MainThread
    fun onContentResolved(content: InAppType.Embedded?, answer: EmbeddedPlaceAnswer)

    fun onContentPending() {}

    @MainThread
    fun onConfigUnavailable(answer: EmbeddedPlaceAnswer)

    @MainThread
    fun onAppResumedOn(activity: Activity)

    @MainThread
    fun onReturnChecked()

    @MainThread
    fun onSessionRenewed()
}

internal interface EmbeddedBlocksRegistry {

    fun register(placeSystemName: PlaceKey, handle: EmbeddedBlockHandle): Closeable

    fun onBlockAppeared(placeSystemName: PlaceKey)

    fun onBlockContentDropped(placeSystemName: PlaceKey)

    fun isLiveSession(sessionEpoch: Long): Boolean

    @MainThread
    fun reserveShow(placeSystemName: PlaceKey, content: InAppType.Embedded, answer: EmbeddedPlaceAnswer): PlaceShowReservation

    @MainThread
    fun giveBackParkedShow(placeSystemName: PlaceKey, sessionEpoch: Long)

    @MainThread
    fun deferUntilReturnChecked(): Boolean

    fun onAppResumedOn(activity: Activity)

    fun onReturnCheckOver()

    fun startListening()
}

internal class EmbeddedBlocksRegistryImpl(
    private val inAppInteractor: InAppInteractor,
    // Read on every use, never captured: the SDK scope is recreated on a soft reinitialization.
    private val scopeProvider: () -> CoroutineScope = { Mindbox.mindboxScope },
    private val monotonicNow: () -> Milliseconds,
    private val isAppPresent: () -> Boolean = {
        loggingRunCatching(defaultValue = true) { LifecycleManager.instance?.hasResumedActivity != false }
    },
) : EmbeddedBlocksRegistry {

    // Weak on purpose: a host with no lifecycle owner to say goodbye — a plain Dialog, a
    // PopupWindow, an app that swaps views itself — only lets its block go, and a strong entry in
    // this process-wide map would keep the block, its view and the Activity behind it alive.
    private val handlesByPlace = mutableMapOf<PlaceKey, MutableList<WeakReference<EmbeddedBlockHandle>>>()
    private val resolvingPlaces = mutableSetOf<PlaceKey>()

    private class SessionRenewal(val sessionEpoch: Long, val tick: Milliseconds)

    private class ResolvePass(
        val trigger: InAppEventType?,
        val includesNonOperation: Boolean = trigger == null,
        val includesAppearance: Boolean = false,
        val renewal: SessionRenewal? = null,
    ) {
        fun mergedWith(newer: ResolvePass): ResolvePass = ResolvePass(
            trigger = newer.trigger ?: trigger,
            includesNonOperation = includesNonOperation || newer.includesNonOperation,
            includesAppearance = includesAppearance || newer.includesAppearance,
            renewal = listOfNotNull(renewal, newer.renewal).maxWithOrNull(
                compareBy<SessionRenewal> { request -> request.sessionEpoch }.thenByDescending { request -> request.tick.interval }
            ),
        )

        fun askedAt(tick: Milliseconds): ResolvePass = ResolvePass(
            trigger = trigger,
            includesNonOperation = includesNonOperation,
            includesAppearance = includesAppearance,
            renewal = renewal?.let { held -> SessionRenewal(held.sessionEpoch, tick) },
        )
    }

    private class HeldRenewal(val pass: ResolvePass, val pending: ResolvePass?) {
        fun mergedWith(newer: HeldRenewal): HeldRenewal = HeldRenewal(
            pass = pass.mergedWith(newer.pass),
            pending = listOfNotNull(pending, newer.pending).reduceOrNull { held, next -> held.mergedWith(next) },
        )
    }

    private val reResolveQueuedPlaces = mutableMapOf<PlaceKey, ResolvePass>()

    private val placesAwaitingNewSession = mutableMapOf<PlaceKey, ResolvePass>()

    private val placesAwaitingReturnCheck = mutableMapOf<PlaceKey, ResolvePass>()

    private val placesAwaitingFirstResume = mutableMapOf<PlaceKey, HeldRenewal>()

    private val placesAppearedMidResolve = mutableSetOf<PlaceKey>()

    private var handledSessionEpoch: Long? = null

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

        if (handledSessionEpoch == null) handledSessionEpoch = inAppInteractor.sessionEpoch
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
            scope.launch {
                inAppInteractor.listenSessionEpoch().collect { sessionEpoch ->
                    runOnMain { onSessionEpoch(sessionEpoch) }
                }
            },
        )
        if (isFirstStart) return

        mindboxLogI(
            "[EmbeddedBlock] Invalidation channels died with the previous SDK scope, resubscribed"
        )
        invalidateAll(reason = "channels resubscribed")
        releaseReturnCheckDeferrals()
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

    override fun isLiveSession(sessionEpoch: Long): Boolean = inAppInteractor.isLiveSession(sessionEpoch)

    @MainThread
    override fun reserveShow(placeSystemName: PlaceKey, content: InAppType.Embedded, answer: EmbeddedPlaceAnswer): PlaceShowReservation =
        inAppInteractor.reservePlaceShow(placeSystemName, content, answer.sessionEpoch)

    @MainThread
    override fun giveBackParkedShow(placeSystemName: PlaceKey, sessionEpoch: Long) {
        if (liveHandles(placeSystemName).any { handle -> handle.mayStillBeSeen }) return
        mindboxLogI("[EmbeddedBlock] The blocks of place '$placeSystemName' have left the screen, giving back the show reserved for the answer that waits for them")
        inAppInteractor.releasePlaceShow(placeSystemName, sessionEpoch)
    }

    @MainThread
    override fun deferUntilReturnChecked(): Boolean {
        restartChannelsIfDead()
        return inAppInteractor.returnCheckPending.value
    }

    override fun onAppResumedOn(activity: Activity) {
        runOnMain {
            forEachLiveHandle { handle -> handle.onAppResumedOn(activity) }
            val held = placesAwaitingFirstResume.toMap()
            placesAwaitingFirstResume.clear()
            held.forEach { (place, renewal) ->
                val handles = liveHandles(place)
                when {
                    handles.any { handle -> handle.isActive || handle.isRenewedWhileAway } -> {
                        mindboxLogI("[EmbeddedBlock] The app is back on screen, asking place '$place' for its new session")
                        resolvePlace(place, renewal.pass.askedAt(monotonicNow()))
                    }
                    else -> renewal.pending?.let { pending -> askHeldOperation(place, pending) }
                }
            }
        }
    }

    override fun onReturnCheckOver() {
        runOnMain { releaseReturnCheckDeferrals() }
    }

    private val EmbeddedBlockHandle.isRenewedWhileAway: Boolean
        get() = isPausedForBackground || isLeftBehind

    private val EmbeddedBlockHandle.mayStillBeSeen: Boolean
        get() = isActive || isPausedForBackground

    private fun forEachLiveHandle(action: (EmbeddedBlockHandle) -> Unit) {
        handlesByPlace.keys.toList().forEach { place ->
            liveHandles(place).forEach { handle -> loggingRunCatching { action(handle) } }
        }
    }

    private fun forgetPlaceIfEmpty(place: PlaceKey) {
        if (handlesByPlace[place]?.isEmpty() != true) return
        handlesByPlace.remove(place)
        reResolveQueuedPlaces.remove(place)
        placesAwaitingNewSession.remove(place)
        placesAwaitingReturnCheck.remove(place)
        placesAwaitingFirstResume.remove(place)
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
        if (pass.trigger != null && inAppInteractor.returnCheckPending.value) {
            mindboxLogI("[EmbeddedBlock] The operation for place '$place' is resolved once the session check of the app's return has run")
            placesAwaitingReturnCheck.merge(place, pass)
            return
        }
        val sessionEpoch = inAppInteractor.sessionEpoch
        if (!inAppInteractor.isLiveSession(sessionEpoch)) {
            mindboxLogI("[EmbeddedBlock] The session is ending, place '$place' is asked once the new session begins")
            placesAwaitingNewSession.merge(place, pass)
            return
        }
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
        val requestedAt = pass.renewal?.takeIf { renewal -> renewal.sessionEpoch == sessionEpoch }?.tick ?: monotonicNow()
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
                    sessionEpoch = sessionEpoch,
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
                    resolveQueued(place, queued)
                }
            }
        }
    }

    private fun resolveQueued(place: PlaceKey, queued: ResolvePass) {
        val isOperationUnseen = queued.trigger != null && queued.renewal == null &&
            liveHandles(place).none { handle -> handle.mayStillBeSeen }
        when {
            !isOperationUnseen -> resolvePlace(place, queued)
            queued.includesNonOperation -> {
                mindboxLogI("[EmbeddedBlock] No block of place '$place' is on screen or away in background, its queued pass is asked without the operation queued with it")
                resolvePlace(place, ResolvePass(trigger = null, includesAppearance = queued.includesAppearance))
            }
            else -> mindboxLogI("[EmbeddedBlock] No block of place '$place' is on screen or away in background, dropping the operation queued for it")
        }
    }

    private fun MutableMap<PlaceKey, ResolvePass>.merge(place: PlaceKey, pass: ResolvePass) {
        this[place] = this[place]?.mergedWith(pass) ?: pass
    }

    private fun onSessionEpoch(sessionEpoch: Long) {
        val handled = handledSessionEpoch
        if (handled != null && sessionEpoch <= handled) return
        handledSessionEpoch = sessionEpoch
        onSessionRenewed(SessionRenewal(sessionEpoch, monotonicNow()))
    }

    private fun onSessionRenewed(renewal: SessionRenewal) {
        val awaiting = placesAwaitingNewSession.toMap()
        placesAwaitingNewSession.clear()
        forEachLiveHandle { handle -> handle.onSessionRenewed() }
        val isPresent = isAppPresent()
        handlesByPlace.keys.toList().forEach { place ->
            val handles = liveHandles(place)
            val pending = awaiting[place]
            val renewalPass = ResolvePass(
                trigger = pending?.trigger,
                includesNonOperation = true,
                includesAppearance = pending?.includesAppearance == true,
                renewal = renewal,
            )
            when {
                handles.isEmpty() -> Unit
                handles.any { handle -> handle.isActive } || (isPresent && handles.any { handle -> handle.isRenewedWhileAway }) -> {
                    mindboxLogI("[EmbeddedBlock] A new session began, asking place '$place' again")
                    resolvePlace(place, renewalPass)
                }
                handles.any { handle -> handle.isRenewedWhileAway } -> {
                    mindboxLogI("[EmbeddedBlock] A new session began while no screen of the app is resumed, place '$place' is asked once one is")
                    val held = HeldRenewal(renewalPass, pending)
                    placesAwaitingFirstResume[place] = placesAwaitingFirstResume[place]?.mergedWith(held) ?: held
                }
                pending != null -> askHeldOperation(place, pending)
                else -> Unit
            }
        }
    }

    private fun releaseReturnCheckDeferrals() {
        if (inAppInteractor.returnCheckPending.value) return
        forEachLiveHandle { handle -> handle.onReturnChecked() }
        val awaiting = placesAwaitingReturnCheck.toMap()
        placesAwaitingReturnCheck.clear()
        awaiting.forEach { (place, pass) ->
            when {
                !handlesByPlace.containsKey(place) -> Unit
                isHandledSessionOver -> {
                    mindboxLogI("[EmbeddedBlock] The session has ended, the operation held for place '$place' through the session check of the app's return is asked once the new session begins")
                    placesAwaitingNewSession.merge(place, pass)
                }
                else -> askHeldOperation(place, pass)
            }
        }
    }

    private fun askHeldOperation(place: PlaceKey, pass: ResolvePass) {
        if (liveHandles(place).none { handle -> handle.mayStillBeSeen }) {
            mindboxLogI("[EmbeddedBlock] No block of place '$place' is on screen or away in background, dropping the operation held for it")
            return
        }
        resolvePlace(place, pass)
    }

    private val isHandledSessionOver: Boolean
        get() = handledSessionEpoch?.let { handled -> !inAppInteractor.isLiveSession(handled) } == true

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
        if (isFromEndedSession(place, answer)) return
        when (outcome) {
            is EmbeddedResolveOutcome.Content -> handleContent(place, outcome, answer)
            EmbeddedResolveOutcome.Empty -> deliverNow(place, null, answer)
            EmbeddedResolveOutcome.ConfigUnavailable -> handleConfigUnavailable(place, answer)
        }
    }

    private fun isFromEndedSession(place: PlaceKey, answer: EmbeddedPlaceAnswer): Boolean {
        if (inAppInteractor.isLiveSession(answer.sessionEpoch)) return false
        dropEndedSessionAnswer(place)
        return true
    }

    private fun dropEndedSessionAnswer(place: PlaceKey) {
        mindboxLogI("[EmbeddedBlock] The answer for place '$place' was computed in a session that has ended, dropping it")
    }

    private fun handleContent(place: PlaceKey, outcome: EmbeddedResolveOutcome.Content, answer: EmbeddedPlaceAnswer) {
        val winner = outcome.variant
        val delayTime = outcome.delayTime?.takeIf { delay -> delay.interval > 0 }
        if (delayTime == null) {
            deliverNow(place, winner, answer)
            return
        }
        val running = delayJobsByPlace[place]
        if (running != null && running.isPending && running.winner.inAppId == winner.inAppId &&
            running.answer.sessionEpoch == answer.sessionEpoch
        ) {
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
            inAppInteractor.awaitReturnChecked()
            val self = coroutineContext[Job]
            runOnMain {
                val due = delayJobsByPlace[place]?.takeIf { pending -> pending.job === self } ?: return@runOnMain
                delayJobsByPlace.remove(place)
                if (!inAppInteractor.markEmbeddedDelayWaitedOut(place, due.winner.inAppId, due.answer.sessionEpoch)) {
                    dropEndedSessionAnswer(place)
                    return@runOnMain
                }
                deliver(place, due.winner, due.answer)
            }
        }
        delayJobsByPlace[place] = PendingDelay(job, winner, answer)
    }

    private fun handleResolveFailure(place: PlaceKey, answer: EmbeddedPlaceAnswer) {
        if (isFromEndedSession(place, answer)) return
        if (keepsRunningDelay(place, answer, "Resolving place '$place' failed")) return
        deliverNow(place, null, answer)
    }

    private fun handleConfigUnavailable(place: PlaceKey, answer: EmbeddedPlaceAnswer) {
        if (keepsRunningDelay(place, answer, "The SDK has no config to answer for place '$place'")) return
        delayJobsByPlace.remove(place)?.job?.cancel()
        deliverConfigUnavailable(place, answer)
    }

    private fun keepsRunningDelay(place: PlaceKey, answer: EmbeddedPlaceAnswer, what: String): Boolean {
        val running = delayJobsByPlace[place]
            ?.takeIf { pending -> pending.isPending && pending.answer.sessionEpoch == answer.sessionEpoch }
            ?: return false
        mindboxLogI("[EmbeddedBlock] $what while winner ${running.winner.inAppId} waits out its delay, keeping the running timer")
        notifyPending(place)
        return true
    }

    private fun deliverNow(place: PlaceKey, content: InAppType.Embedded?, answer: EmbeddedPlaceAnswer) {
        delayJobsByPlace.remove(place)?.job?.cancel()
        deliver(place, content, answer)
    }

    private fun deliverConfigUnavailable(place: PlaceKey, answer: EmbeddedPlaceAnswer) {
        if (!inAppInteractor.releasePlaceShow(place, answer.sessionEpoch)) {
            dropEndedSessionAnswer(place)
            return
        }
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
            inAppInteractor.releasePlaceShow(place, answer.sessionEpoch)
            return
        }
        val showable = content?.takeIf { winner -> winner.pageLayer != null }
        val defersReservation = showable != null && handles.none { handle -> handle.mayStillBeSeen }
        if (showable != null && defersReservation) {
            mindboxLogI("[EmbeddedBlock] The blocks of place '$place' are all off screen, the show of ${showable.inAppId} is reserved once one is back")
        }
        val delivered = when (content?.takeUnless { defersReservation }?.let { winner -> inAppInteractor.reservePlaceShow(place, winner, answer.sessionEpoch) }) {
            PlaceShowReservation.RESERVED -> content
            PlaceShowReservation.REFUSED -> {
                mindboxLogI(
                    "[EmbeddedBlock] In-app ${content?.inAppId} won place '$place' but the show " +
                        "budgets are spent, the place has nothing to show"
                )
                null
            }
            PlaceShowReservation.STALE -> {
                dropEndedSessionAnswer(place)
                return
            }
            null -> content
        }
        if (delivered?.pageLayer == null && !inAppInteractor.releasePlaceShow(place, answer.sessionEpoch)) {
            dropEndedSessionAnswer(place)
            return
        }
        val deliveredAnswer = if (defersReservation) answer.copy(isShowReserved = false) else answer
        handles.forEach { handle ->
            loggingRunCatching { handle.onContentResolved(delivered, deliveredAnswer) }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }
}
