package cloud.mindbox.mobile_sdk.embedded

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.gatedTags
import cloud.mindbox.mobile_sdk.inapp.data.managers.SEND_INAPP_TAGS_FEATURE
import cloud.mindbox.mobile_sdk.inapp.domain.extensions.sendBlockFailureWithContext
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.PlaceShowReservation
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFailureTracker
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.WaitBudgetPhase
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.embedded.webview.DataPushOutcome
import cloud.mindbox.mobile_sdk.embedded.webview.EmbeddedUpdatableContentProvider
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.managers.LifecycleManager
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.PlaceKey
import cloud.mindbox.mobile_sdk.models.SessionEpoch
import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.Constants
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.Closeable

internal class EmbeddedBlockContentController(
    placeSystemName: String? = null,
    configTimeout: Milliseconds = Constants.Embedded.defaultConfigTimeout,
    private val readyTimeout: Milliseconds = Constants.WebView.readyTimeout,
    private val providerFactory: (InAppType.Embedded, Milliseconds) -> EmbeddedContentProvider?,
    private val blocksRegistry: () -> EmbeddedBlocksRegistry? = {
        loggingRunCatching(defaultValue = null) {
            if (MindboxDI.isInitialized()) MindboxDI.appModule.embeddedBlocksRegistry else null
        }
    },
    private val monotonicNow: () -> Milliseconds = { Milliseconds(SystemClock.elapsedRealtime()) },
    private val failureTracker: () -> InAppFailureTracker? = {
        if (MindboxDI.isInitialized()) MindboxDI.appModule.inAppFailureTracker else null
    },
    private val isTagsFeatureEnabled: () -> Boolean = {
        MindboxDI.isInitialized() && MindboxDI.appModule.featureToggleManager.isEnabled(SEND_INAPP_TAGS_FEATURE)
    },
    private val hasConfig: () -> Boolean = {
        loggingRunCatching(defaultValue = false) {
            MindboxDI.isInitialized() && MindboxDI.appModule.mobileConfigRepositoryIfCreated?.hasConfig() == true
        }
    },
    private val hostActivity: () -> Activity? = { null },
    private val isAppInForeground: () -> Boolean = {
        loggingRunCatching(defaultValue = true) { LifecycleManager.instance?.isInForegroundBesides(hostActivity()) != false }
    },
) : EmbeddedBlockHandle {

    private val placeSystemName: PlaceKey? = placeSystemName?.takeIf { it.isNotBlank() }?.let(PlaceKey::of)

    var onStateChange: ((EmbeddedBlockState) -> Unit)? = null

    val contentView: View?
        get() = provider?.contentView

    override val isActive: Boolean
        get() = isStarted && !isReleased && !hasGivenUp

    override val isPausedForBackground: Boolean
        get() = isAwayInBackground && !isStarted && !isReleased

    override val isLeftBehind: Boolean
        get() = leftBehind && holdsLiveContent && !isStarted && !isReleased

    private var provider: EmbeddedContentProvider? = null
    private val updatablePage: EmbeddedUpdatableContentProvider?
        get() = provider as? EmbeddedUpdatableContentProvider
    private var failureTrackerCache: InAppFailureTracker? = null
    private var isStarted = false
    private var isReleased = false
    private var hasGivenUp = false
    private var isAwayInBackground = false
    private var leftBehind = false

    var lastReportedState: EmbeddedBlockState? = null
        private set
    val isRetainable: Boolean
        get() = !isReleased && heldOutcome == null &&
            lastReportedState == EmbeddedBlockState.Ready && provider?.contentView != null

    val heldOutcome: EmbeddedBlockState?
        get() = heldCollapse ?: EmbeddedBlockState.Empty.takeIf { isShownPageEmptied && !hasParkedShowableContent }

    private val isShownPageEmptied: Boolean
        get() = updatablePage?.rendersNothing == true && lastReportedState?.nothingToShow != true

    private var registration: Closeable? = null
    private var configJob: Job? = null

    private sealed interface ParkedAnswer {
        val answer: EmbeddedPlaceAnswer
        val selectedInAppId: String?

        class Resolved(val content: InAppType.Embedded?, override val answer: EmbeddedPlaceAnswer) : ParkedAnswer {
            override val selectedInAppId: String? = content?.inAppId
        }

        class ConfigUnavailable(override val answer: EmbeddedPlaceAnswer, override val selectedInAppId: String?) : ParkedAnswer
    }

    private var parkedAnswer: ParkedAnswer? = null

    private var deferredParkedMayHold: Boolean? = null

    private val hasPendingContent: Boolean
        get() = (parkedAnswer as? ParkedAnswer.Resolved)?.content != null

    private val hasParkedShowableContent: Boolean
        get() = (parkedAnswer as? ParkedAnswer.Resolved)?.content.isShowable

    private val hasNoContentComing: Boolean
        get() = provider == null && !hasPendingContent

    private var heldCollapse: EmbeddedBlockState? = null

    private var appliedSessionEpoch: SessionEpoch? = null

    private class DataPush(val descriptor: PageDescriptor, val sessionEpoch: SessionEpoch?)

    private var dataPush: DataPush? = null

    private val liveDataPush: DataPush?
        get() = dataPush?.takeUnless { updatablePage?.isDataPushAbandoned == true }

    private val pageSessionEpoch: SessionEpoch?
        get() = liveDataPush?.sessionEpoch ?: appliedSessionEpoch

    private var dueSessionRefresh: EmbeddedPlaceAnswer? = null

    /** What the shown page was built from — the "same content" dedup key. */
    private var appliedDescriptor: PageDescriptor? = null

    /** The snapshot of the applied content — the failure events' id and tags come from it. */
    private var appliedContent: InAppType.Embedded? = null

    /**
     * The applied content, split the way the dedup needs it: [isSamePage] is the page's identity —
     * the winner and the address the page was built from — while [params] are data a live page can
     * take over the bridge. A changed address is a different page even under the same winner.
     */
    private data class PageDescriptor(
        val inAppId: String,
        val baseUrl: String?,
        val contentUrl: String?,
        val params: Map<String, String>,
    ) {
        fun isSamePage(other: PageDescriptor): Boolean =
            inAppId == other.inAppId && baseUrl == other.baseUrl && contentUrl == other.contentUrl
    }

    private fun descriptorOf(inAppId: String, layer: Layer.WebViewLayer): PageDescriptor =
        PageDescriptor(
            inAppId = inAppId,
            baseUrl = layer.baseUrl,
            contentUrl = layer.contentUrl,
            params = layer.params,
        )

    private var updateEpoch = 0

    private var attemptStartTick: Milliseconds? = null

    private var hasPendingDelivery = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val configWaitDuration: Milliseconds = sanitizedConfigTimeout(configTimeout, this.placeSystemName)

    private val configBudget = EmbeddedBlockWaitBudget(configWaitDuration, mainHandler) { onConfigTimeout() }
    private val readyBudget = EmbeddedBlockWaitBudget(readyTimeout, mainHandler) { onReadyTimeout() }

    fun start() {
        if (isReleased) return
        isStarted = true
        hasGivenUp = false
        val place = placeSystemName ?: run {
            report(EmbeddedBlockState.Empty)
            return
        }

        if (lastReportedState?.nothingToShow == true) {
            mindboxLogI("[EmbeddedBlock] Returning to a block with nothing on it, asking for its content again")
            dropProvider()
            appliedDescriptor = null
        }

        val isBackFromBackground = isAwayInBackground
        isAwayInBackground = false
        val isBackAfterBeingLeftBehind = leftBehind
        leftBehind = false
        if (isBackAfterBeingLeftBehind) resumeAfterBeingLeftBehind()
        val hasLandedCollapse = parkedAnswer?.let { parked ->
            if (parked.waitsForReturnCheck && blocksRegistry()?.deferUntilReturnChecked() == true) {
                mindboxLogI("[EmbeddedBlock] Block '$placeSystemName' is back before the session check of the app's return, its parked answer waits for it")
                deferredParkedMayHold = isBackFromBackground
                false
            } else {
                landParkedAnswer(mayHold = isBackFromBackground)
            }
        } == true

        provider?.let { current ->
            current.onStateChange = ::onProviderState
            readyBudget.armIfNeeded()
            if (!startProvider(current, appliedContent)) return
        }

        if (!ensureRegistered()) {
            beginWaitingForContent()
            return
        }
        if (hasLandedCollapse) {
            mindboxLogI("[EmbeddedBlock] Block '$placeSystemName' is back collapsed by the answer parked for it, asking its place again")
        } else if (hasNoContentComing) {
            beginWaitingForContent()
        }
        blocksRegistry()?.onBlockAppeared(place)
    }

    fun pause() {
        isStarted = false
        deferredParkedMayHold = null
        isAwayInBackground = !isAppInForeground()
        // A pause, not a reset: leaving the screen does not cancel an attempt already started,
        // and the clocks stop with the user's waiting.
        readyBudget.pause()
        configBudget.pause()
        provider?.pause()
        if (isAwayInBackground) {
            mindboxLogI("[EmbeddedBlock] Block '$placeSystemName' paused by the app going to background, it keeps what it shows")
            return
        }
        collapseHeldAnswer()
    }

    fun release() {
        isStarted = false
        isReleased = true
        readyBudget.reset()
        configBudget.reset()
        stopWaitingForDi()
        registration?.let { loggingRunCatching { it.close() } }
        registration = null
        dropProvider()
        notifyContentDropped()
    }

    override fun onContentResolved(content: InAppType.Embedded?, answer: EmbeddedPlaceAnswer) {
        if (!acceptAnswer("Content")) return
        if (content != null && content.pageLayer == null) reportInternalFailure(content, winnerWithoutPage(content))
        if (!takesAnswerNow(waitsForReturnCheck = content.isShowable)) {
            if (isActive) {
                mindboxLogI("[EmbeddedBlock] Content for '$placeSystemName' waits for the session check of the app's return")
            } else {
                mindboxLogI("[EmbeddedBlock] Content for '$placeSystemName' arrived while paused, deferring it")
            }
            val earlierOfSameInApp = parkedAnswer?.takeIf { parked -> content != null && parked.selectedInAppId == content.inAppId }
            parkedAnswer = ParkedAnswer.Resolved(content, keepingFirstSelectionOfNewSession(answer, earlierOfSameInApp?.answer))
            if (content.isShowable) supersedeHeldOutcome()
            return
        }
        dropContentWaitingForReturnCheck()
        applyAnswer(content, answer, mayHold = true, isParked = false)
    }

    private val ParkedAnswer.waitsForReturnCheck: Boolean
        get() = this is ParkedAnswer.Resolved && content.isShowable

    private val InAppType.Embedded?.isShowable: Boolean
        get() = this?.pageLayer != null

    private fun takesAnswerNow(waitsForReturnCheck: Boolean): Boolean =
        isActive && (deferredParkedMayHold == null || !waitsForReturnCheck)

    private fun dropContentWaitingForReturnCheck() {
        if (deferredParkedMayHold == null) return
        mindboxLogI("[EmbeddedBlock] Block '$placeSystemName' has nothing to show before the session check of the app's return, dropping the content parked for it")
        deferredParkedMayHold = null
        parkedAnswer = null
    }

    override fun onReturnChecked() {
        deferredParkedMayHold?.let { mayHold ->
            deferredParkedMayHold = null
            if (isActive && !applyParkedAnswer(mayHold) && hasNoContentComing) beginWaitingForContent()
        }
        sendDueSessionRefresh()
    }

    override fun onAppResumedOn(activity: Activity) {
        if (!isPausedForBackground) return
        val host = hostActivity() ?: return
        if (host === activity) return
        mindboxLogI("[EmbeddedBlock] The app came back from background on another screen, block '$placeSystemName' has left the screen")
        leaveScreenPausedForBackground()
    }

    fun onLeftScreen() {
        if (!isPausedForBackground) return
        if (hostActivity()?.isChangingConfigurations == true) return
        mindboxLogI("[EmbeddedBlock] Block '$placeSystemName' left its screen before it was shown again after the app went to background")
        leaveScreenPausedForBackground()
    }

    private fun leaveScreenPausedForBackground() {
        isAwayInBackground = false
        applyParkedRefusal()
        collapseHeldAnswer()
        giveBackParkedShow()
        leftBehind = holdsLiveContent
    }

    private fun applyParkedRefusal() {
        val parked = parkedAnswer ?: return
        if (parked.waitsForReturnCheck || isOfEndedSession(parked.answer.sessionEpoch)) return
        mindboxLogI("[EmbeddedBlock] Block '$placeSystemName' has left the screen, applying the answer with nothing to show parked for it")
        applyParkedAnswer(mayHold = false)
    }

    private fun giveBackParkedShow() {
        val parked = parkedAnswer as? ParkedAnswer.Resolved ?: return
        val place = placeSystemName ?: return
        if (!parked.answer.isShowReserved || !parked.content.isShowable) return
        parkedAnswer = ParkedAnswer.Resolved(parked.content, parked.answer.copy(isShowReserved = false))
        blocksRegistry()?.giveBackParkedShow(place, parked.answer.sessionEpoch)
    }

    override fun onSessionRenewed() {
        if (isStarted || isReleased || isAwayInBackground || !holdsLiveContent) return
        mindboxLogI("[EmbeddedBlock] A new session began while block '$placeSystemName' was off screen, its place is asked again and the answer waits for its return")
        leftBehind = true
    }

    private fun applyParkedAnswer(mayHold: Boolean): Boolean {
        val parked = parkedAnswer ?: return false
        parkedAnswer = null
        return applyParked(parked, mayHold)
    }

    private fun landParkedAnswer(mayHold: Boolean): Boolean =
        applyParkedAnswer(mayHold) && lastReportedState?.nothingToShow == true

    private fun isOfEndedSession(sessionEpoch: SessionEpoch?): Boolean =
        sessionEpoch != null && blocksRegistry()?.isLiveSession(sessionEpoch) == false

    private fun resumeAfterBeingLeftBehind() {
        if (parkedAnswer?.let { parked -> isOfEndedSession(parked.answer.sessionEpoch) } == true) {
            parkedAnswer = null
            dropAnswerOfEndedSession()
        }
        if (parkedAnswer != null || !isOfEndedSession(pageSessionEpoch)) return
        mindboxLogI(
            "[EmbeddedBlock] Block '$placeSystemName' is back on its screen, " +
                "keeping its page of an ended session until the new session answers"
        )
    }

    private fun applyParked(parked: ParkedAnswer, mayHold: Boolean): Boolean {
        if (isOfEndedSession(parked.answer.sessionEpoch)) {
            dropAnswerOfEndedSession()
            return false
        }
        return when (parked) {
            is ParkedAnswer.Resolved -> applyAnswer(parked.content, parked.answer, mayHold, isParked = true)
            is ParkedAnswer.ConfigUnavailable -> {
                applyConfigUnavailable(parked.answer, mayHold)
                true
            }
        }
    }

    private fun dropAnswerOfEndedSession() {
        if (isHoldingContent) {
            mindboxLogI(
                "[EmbeddedBlock] The answer parked for '$placeSystemName' was given in a session that has ended, " +
                    "dropping it and keeping the content until the place answers again"
            )
        } else {
            mindboxLogI(
                "[EmbeddedBlock] The answer parked for '$placeSystemName' was given in a session that has ended, " +
                    "dropping it; the block has nothing to show until the place answers again"
            )
        }
    }

    private fun applyAnswer(
        content: InAppType.Embedded?,
        answer: EmbeddedPlaceAnswer,
        mayHold: Boolean,
        isParked: Boolean,
    ): Boolean {
        val place = placeSystemName
        val toReserve = content?.takeIf { winner -> (isParked || !answer.isShowReserved) && winner.pageLayer != null }
        if (toReserve == null || place == null) {
            applyResolved(content, answer, mayHold)
            return true
        }
        when (blocksRegistry()?.reserveShow(place, toReserve, answer) ?: PlaceShowReservation.RESERVED) {
            PlaceShowReservation.RESERVED -> applyResolved(content, answer, mayHold)
            PlaceShowReservation.REFUSED -> {
                mindboxLogI("[EmbeddedBlock] In-app ${toReserve.inAppId} is back on screen at '$place' but the show budgets are spent, the place has nothing to show")
                applyResolved(null, answer, mayHold)
            }
            PlaceShowReservation.STALE -> {
                dropAnswerOfEndedSession()
                return false
            }
        }
        return true
    }

    override fun onContentPending() {
        if (isReleased || hasGivenUp) return
        configBudget.reset()
        hasPendingDelivery = true
    }

    override fun onConfigUnavailable(answer: EmbeddedPlaceAnswer) {
        if (!acceptAnswer("The SDK's answer")) return
        val earlierParked = parkedAnswer
        parkedAnswer = null
        mindboxLogW("[EmbeddedBlock] The SDK has no config to answer for '$placeSystemName', sending its wait failure")
        sendWaitBudgetExceeded(
            waited = attemptStartTick?.let(::elapsedSince) ?: Milliseconds(0L),
            phase = WaitBudgetPhase.CONFIG_MISSING,
        )
        if (!takesAnswerNow(waitsForReturnCheck = false)) {
            mindboxLogI("[EmbeddedBlock] The failure for '$placeSystemName' arrived while paused, deferring it")
            val selectedInAppId = earlierParked?.takeIf { parked -> parked.answer.sessionEpoch == answer.sessionEpoch }?.selectedInAppId
            parkedAnswer = ParkedAnswer.ConfigUnavailable(keepingFirstSelectionOfNewSession(answer, earlierParked?.answer), selectedInAppId)
            return
        }
        dropContentWaitingForReturnCheck()
        applyConfigUnavailable(answer, mayHold = true)
    }

    private fun applyConfigUnavailable(answer: EmbeddedPlaceAnswer, mayHold: Boolean) {
        collapseOrHold(EmbeddedBlockState.Failed(FailureReason.WAIT_BUDGET_EXCEEDED), answer, mayHold)
    }

    private fun collapseOrHold(state: EmbeddedBlockState, answer: EmbeddedPlaceAnswer, mayHold: Boolean) {
        if (mayHold && holdsShownContent(answer)) {
            holdCollapse(state)
            return
        }
        forgetAppliedContent()
        report(state)
    }

    private fun holdsShownContent(answer: EmbeddedPlaceAnswer): Boolean =
        !answer.isByOperation && lastReportedState == EmbeddedBlockState.Ready

    private fun holdCollapse(state: EmbeddedBlockState) {
        mindboxLogI(
            "[EmbeddedBlock] Place '$placeSystemName' has nothing to show while its content is on screen, " +
                "keeping it until the block leaves the screen"
        )
        heldCollapse = state
        dueSessionRefresh = null
        updatablePage?.withholdShow(true)
    }

    private fun releaseHeldCollapse() {
        heldCollapse = null
        updatablePage?.withholdShow(false)
    }

    private fun supersedeHeldOutcome() {
        if (heldCollapse == null) {
            if (isShownPageEmptied) keepEmptiedPageForParkedContent()
            return
        }
        mindboxLogI("[EmbeddedBlock] Content parked for '$placeSystemName' replaces the collapse held for it, the page's show waits for that content")
        heldCollapse = null
    }

    private fun keepEmptiedPageForParkedContent() {
        mindboxLogI("[EmbeddedBlock] The page of block '$placeSystemName' renders nothing while content is parked for the block, keeping the page; its show waits for that content")
        updatablePage?.withholdShow(true)
    }

    private fun collapseHeldAnswer() {
        val held = heldOutcome ?: return
        heldCollapse = null
        mindboxLogI("[EmbeddedBlock] Block '$placeSystemName' left the screen, collapsing the content it has nothing to show for")
        forgetAppliedContent()
        report(held)
    }

    private fun onPageEmptied() {
        if (isReleased || !isShownPageEmptied) return
        if (hasParkedShowableContent) {
            keepEmptiedPageForParkedContent()
            return
        }
        if (isStarted || isAwayInBackground) return
        mindboxLogI("[EmbeddedBlock] The page of block '$placeSystemName' was left with nothing to show after the block left the screen, collapsing it")
        forgetAppliedContent()
        report(EmbeddedBlockState.Empty)
    }

    private fun acceptAnswer(what: String): Boolean {
        if (isReleased) return false
        if (hasGivenUp) {
            mindboxLogI(
                "[EmbeddedBlock] $what for '$placeSystemName' arrived after the block gave up " +
                    "waiting, dropping it; the next appearance on screen asks afresh"
            )
            notifyContentDropped()
            return false
        }
        configBudget.reset()
        hasPendingDelivery = false
        return true
    }

    private fun sendWaitBudgetExceeded(waited: Milliseconds, phase: WaitBudgetPhase) {
        val place = placeSystemName ?: return
        val tracker = resolveFailureTracker() ?: return
        loggingRunCatching { tracker.sendPlaceWaitBudgetExceeded(place, waited, phase) }
    }

    private fun applyResolved(content: InAppType.Embedded?, answer: EmbeddedPlaceAnswer, mayHold: Boolean) {
        if (content == null) {
            mindboxLogI("[EmbeddedBlock] Nothing to show for place '$placeSystemName'")
            collapseOrHold(EmbeddedBlockState.Empty, answer, mayHold)
            return
        }
        val layer = content.pageLayer ?: run {
            collapseOrHold(EmbeddedBlockState.Failed(FailureReason.UNKNOWN_ERROR), answer, mayHold)
            return
        }
        val descriptor = descriptorOf(content.inAppId, layer)
        val current = provider
        val sentPush = liveDataPush

        if (current != null && descriptor == (sentPush?.descriptor ?: appliedDescriptor) && lastReportedState?.nothingToShow != true) {
            refreshMetricsSnapshot(current, content)
            when {
                current is EmbeddedUpdatableContentProvider && isNewSessionFor(answer) -> refreshForNewSession(current, answer, content)
                sentPush != null -> mindboxLogI(
                    "[EmbeddedBlock] Same winner ${content.inAppId} for '$placeSystemName' with the params already on their way to its page, sending nothing"
                )
                else -> mindboxLogI("[EmbeddedBlock] Same winner ${content.inAppId} for '$placeSystemName', keeping the content")
            }
            releaseHeldCollapse()
            return
        }
        if (lastReportedState?.nothingToShow == true) {
            mindboxLogI("[EmbeddedBlock] Delivery for a collapsed block '$placeSystemName', rebuilding the content")
            recreateProvider(content, layer, answer)
            return
        }
        if (current is EmbeddedUpdatableContentProvider &&
            appliedDescriptor?.isSamePage(descriptor) == true &&
            lastReportedState == EmbeddedBlockState.Ready
        ) {
            mindboxLogI("[EmbeddedBlock] Same winner ${content.inAppId} with new params, updating the content in place")
            refreshMetricsSnapshot(current, content)
            pushPageData(current, layer, content, answer)
            releaseHeldCollapse()
            return
        }
        recreateProvider(content, layer, answer)
    }

    private fun winnerWithoutPage(content: InAppType.Embedded): String = "Winner ${content.inAppId} has no webview layer"

    private fun isNewSessionFor(answer: EmbeddedPlaceAnswer): Boolean =
        pageSessionEpoch?.let { pageSession -> answer.sessionEpoch > pageSession } == true

    private fun keepingFirstSelectionOfNewSession(answer: EmbeddedPlaceAnswer, earlier: EmbeddedPlaceAnswer?): EmbeddedPlaceAnswer {
        val first = earlier?.takeIf { held -> held.sessionEpoch == answer.sessionEpoch && isNewSessionFor(answer) } ?: return answer
        return answer.copy(selectionTime = first.selectionTime)
    }

    private fun refreshForNewSession(
        current: EmbeddedUpdatableContentProvider,
        answer: EmbeddedPlaceAnswer,
        content: InAppType.Embedded,
    ) {
        val layer = content.pageLayer ?: return
        if (lastReportedState != EmbeddedBlockState.Ready) {
            mindboxLogI(
                "[EmbeddedBlock] A new session picked ${content.inAppId} for '$placeSystemName' again while " +
                    "its page loads, refreshing the page once it renders"
            )
            val due = keepingFirstSelectionOfNewSession(answer, dueSessionRefresh)
            dueSessionRefresh = due
            current.scheduleSessionRefresh(due.sessionEpoch, due.selectionTime)
            return
        }
        mindboxLogI("[EmbeddedBlock] A new session picked ${content.inAppId} for '$placeSystemName' again, refreshing the page in place")
        pushPageData(current, layer, content, answer)
    }

    private val isDueSessionRefreshSendable: Boolean
        get() = dueSessionRefresh != null && isActive && lastReportedState == EmbeddedBlockState.Ready

    private fun sendDueSessionRefreshOnceReturnChecked() {
        if (!isDueSessionRefreshSendable) return
        if (blocksRegistry()?.deferUntilReturnChecked() == true) {
            mindboxLogI("[EmbeddedBlock] The page of '$placeSystemName' rendered before the session check of the app's return, its new session's data waits for the check")
            return
        }
        sendDueSessionRefresh()
    }

    private fun sendDueSessionRefresh() {
        if (!isDueSessionRefreshSendable) return
        val answer = dueSessionRefresh ?: return
        val current = updatablePage ?: return
        dueSessionRefresh = null
        if (isOfEndedSession(answer.sessionEpoch)) {
            mindboxLogI("[EmbeddedBlock] The session that picked the page of '$placeSystemName' again has ended before the page rendered, dropping its refresh")
            return
        }
        refreshForNewSession(current, answer, appliedContent ?: return)
    }

    private fun pushPageData(
        current: EmbeddedUpdatableContentProvider,
        layer: Layer.WebViewLayer,
        content: InAppType.Embedded,
        answer: EmbeddedPlaceAnswer,
    ) {
        val descriptor = descriptorOf(content.inAppId, layer)
        val epoch = ++updateEpoch
        val isNewSession = isNewSessionFor(answer)
        val push = DataPush(descriptor, sessionEpoch = if (isNewSession) answer.sessionEpoch else pageSessionEpoch)
        dataPush = push
        val onResult: (DataPushOutcome) -> Unit = { outcome ->
            mainHandler.post {
                if (isReleased || provider !== current || epoch != updateEpoch) return@post
                dataPush = null
                when {
                    outcome == DataPushOutcome.CONFIRMED -> {
                        appliedDescriptor = descriptor
                        appliedContent = content
                        appliedSessionEpoch = push.sessionEpoch
                    }
                    outcome == DataPushOutcome.ABANDONED -> mindboxLogI(
                        "[EmbeddedBlock] The data push to the page of '$placeSystemName' was abandoned before the page answered, " +
                            "the place's next answer sends it again"
                    )
                    heldOutcome != null -> {
                        mindboxLogW("[EmbeddedBlock] In-place update over the bridge failed while the block has nothing to show, not rebuilding")
                    }
                    isOfEndedSession(answer.sessionEpoch) -> mindboxLogI(
                        "[EmbeddedBlock] The page of '$placeSystemName' did not take the data of a session that has ended, " +
                            "keeping it until the new session answers"
                    )
                    else -> {
                        mindboxLogW("[EmbeddedBlock] In-place update over the bridge failed, recreating the content")
                        recreateProvider(content, layer, answerForRebuild(current, answer, isNewSession))
                    }
                }
            }
        }
        runCatching {
            if (isNewSession) {
                current.refreshForSession(layer.params, answer.sessionEpoch, answer.selectionTime, onResult)
            } else {
                current.updateParams(layer.params, onResult)
            }
        }.onFailure { error ->
            mindboxLogW("[EmbeddedBlock] In-place update crashed ($error), recreating the content")
            recreateProvider(content, layer, answerForRebuild(current, answer, isNewSession))
        }
    }

    private fun answerForRebuild(
        failed: EmbeddedUpdatableContentProvider,
        answer: EmbeddedPlaceAnswer,
        isNewSession: Boolean,
    ): EmbeddedPlaceAnswer {
        if (!isNewSession) return answer
        val counted = loggingRunCatching(defaultValue = null) { failed.timeToDisplaySoFar(answer.sessionEpoch) } ?: return answer
        return answer.copy(selectionTime = counted)
    }

    private fun refreshMetricsSnapshot(current: EmbeddedContentProvider, content: InAppType.Embedded) {
        if (current !is EmbeddedUpdatableContentProvider) return
        val applied = appliedContent
        if (applied != null && applied.frequency == content.frequency && applied.tags == content.tags) return
        mindboxLogI("[EmbeddedBlock] Same winner ${content.inAppId}: the config changed its frequency/tags, refreshing the snapshot")
        loggingRunCatching { current.refreshMetricsSnapshot(content.frequency, content.tags) }
        appliedContent = content
    }

    private fun recreateProvider(content: InAppType.Embedded, layer: Layer.WebViewLayer, answer: EmbeddedPlaceAnswer) {
        dropProvider()
        if (lastReportedState != EmbeddedBlockState.Loading || attemptStartTick == null) {
            attemptStartTick = monotonicNow()
        }
        val created = loggingRunCatching(defaultValue = null) { providerFactory(content, answer.selectionTime) } ?: run {
            failInternally(content, "Could not build content for ${content.inAppId}")
            return
        }
        provider = created
        appliedDescriptor = descriptorOf(content.inAppId, layer)
        appliedContent = content
        appliedSessionEpoch = answer.sessionEpoch
        (created as? EmbeddedUpdatableContentProvider)?.let { page ->
            page.confirmForSession(answer.sessionEpoch)
            page.onRendersNothing = ::onPageEmptied
        }
        created.onStateChange = ::onProviderState
        if (isStarted) {
            readyBudget.reset()
            readyBudget.armIfNeeded()
            startProvider(created, content)
        }
    }

    override val isHoldingContent: Boolean
        get() = lastReportedState is EmbeddedBlockState.Loading ||
            lastReportedState == EmbeddedBlockState.Ready ||
            hasPendingContent

    override val holdsLiveContent: Boolean
        get() = isHoldingContent && (hasPendingContent || provider?.isAttemptAlive != false)

    private fun onProviderState(state: EmbeddedBlockState) {
        if (state is EmbeddedBlockState.Ready && provider?.contentView == null) {
            dropProvider()
            failInternally(appliedContent, "Ready content for '$placeSystemName' has no view")
            return
        }
        report(state)
        if (state == EmbeddedBlockState.Ready) sendDueSessionRefreshOnceReturnChecked()
    }

    private fun report(state: EmbeddedBlockState) {
        if (state !is EmbeddedBlockState.Loading) {
            readyBudget.reset()
            attemptStartTick = null
        }
        if (state.nothingToShow) {
            heldCollapse = null
            dueSessionRefresh = null
        }
        if (state == lastReportedState) return
        lastReportedState = state
        onStateChange?.let { listener -> loggingRunCatching { listener(state) } }
        if (state.nothingToShow) notifyContentDropped()
    }

    private fun startProvider(current: EmbeddedContentProvider, content: InAppType.Embedded?): Boolean =
        runCatching { current.start() }
            .onFailure { error ->
                dropProvider()
                failInternally(content, "Starting content for '$placeSystemName' crashed", error)
            }
            .isSuccess

    private fun failInternally(content: InAppType.Embedded?, description: String, error: Throwable? = null) {
        reportInternalFailure(content, description, error)
        report(EmbeddedBlockState.Failed(FailureReason.UNKNOWN_ERROR))
    }

    private fun reportInternalFailure(content: InAppType.Embedded?, description: String, error: Throwable? = null) {
        if (!sendFailure(content, FailureReason.UNKNOWN_ERROR, "[EmbeddedBlock] $description", error)) {
            mindboxLogE("[EmbeddedBlock] $description, reporting failure", error)
        }
    }

    private fun sendFailure(
        content: InAppType.Embedded?,
        code: FailureReason,
        description: String,
        error: Throwable? = null,
    ): Boolean {
        val failed = content ?: return false
        val tracker = resolveFailureTracker() ?: return false
        loggingRunCatching {
            tracker.sendBlockFailureWithContext(
                inAppId = failed.inAppId,
                failureReason = code,
                errorDescription = description,
                throwable = error,
                tags = failed.tags.gatedTags(isTagsFeatureEnabled()),
            )
        }
        return true
    }

    private fun resolveFailureTracker(): InAppFailureTracker? =
        failureTrackerCache ?: failureTracker()?.also { tracker -> failureTrackerCache = tracker }

    private fun notifyContentDropped() {
        val place = placeSystemName ?: return
        loggingRunCatching { blocksRegistry()?.onBlockContentDropped(place) }
    }

    private fun ensureRegistered(): Boolean {
        if (registration != null) return true
        val place = placeSystemName ?: return false
        val registry = blocksRegistry() ?: run {
            waitForDi()
            return false
        }
        stopWaitingForDi()
        registration = registry.register(place, this)
        return true
    }

    private fun waitForDi() {
        if (configJob != null) return
        report(EmbeddedBlockState.Loading)
        configJob = loggingRunCatching(defaultValue = null) {
            MindboxPreferences.inAppConfigFlow
                .onEach {
                    mainHandler.post {
                        if (isReleased || registration != null) return@post
                        if (ensureRegistered() && isStarted) {
                            placeSystemName?.let { place -> blocksRegistry()?.onBlockAppeared(place) }
                        }
                    }
                }
                .launchIn(Mindbox.mindboxScope)
        }
    }

    private fun stopWaitingForDi() {
        val job = configJob ?: return
        configJob = null
        loggingRunCatching { job.cancel() }
    }

    private fun beginWaitingForContent() {
        report(EmbeddedBlockState.Loading)
        attemptStartTick = attemptStartTick ?: monotonicNow()
        if (!hasEverResolved()) {
            configBudget.armIfNeeded()
        }
    }

    private fun onConfigTimeout() {
        if (isReleased || hasEverResolved()) return
        mindboxLogW(
            "[EmbeddedBlock] No answer within ${configWaitDuration.interval}ms of waiting for " +
                "'$placeSystemName', collapsing; a later answer is dropped, the next appearance " +
                "on screen asks afresh"
        )
        hasGivenUp = true
        configBudget.reset()
        sendWaitBudgetExceeded(
            waited = configWaitDuration,
            phase = if (hasConfig()) WaitBudgetPhase.RESOLVE_PENDING else WaitBudgetPhase.CONFIG_MISSING,
        )
        report(EmbeddedBlockState.Failed(FailureReason.WAIT_BUDGET_EXCEEDED))
    }

    private fun hasEverResolved(): Boolean =
        provider != null || parkedAnswer != null || appliedDescriptor != null || hasPendingDelivery

    private fun onReadyTimeout() {
        if (!isStarted || provider == null) return
        mindboxLogW(
            "[EmbeddedBlock] Page for '$placeSystemName' stayed silent for " +
                "${readyTimeout.interval}ms of waiting, reporting failure",
        )
        sendFailure(
            content = appliedContent,
            code = FailureReason.PRESENTATION_FAILED,
            description = "Page did not send contentRendered within ${readyTimeout.interval / 1_000}s",
        )
        hasGivenUp = true
        dropProvider()
        report(EmbeddedBlockState.Failed(FailureReason.PRESENTATION_FAILED))
    }

    private fun forgetAppliedContent() {
        dropProvider()
        appliedDescriptor = null
        appliedContent = null
    }

    private fun elapsedSince(start: Milliseconds): Milliseconds = Milliseconds(monotonicNow().interval - start.interval)

    private fun dropProvider() {
        readyBudget.reset()
        provider?.let { current -> loggingRunCatching { current.release() } }
        provider = null
        appliedSessionEpoch = null
        dataPush = null
        dueSessionRefresh = null
        heldCollapse = null
    }

    private companion object {
        fun sanitizedConfigTimeout(requested: Milliseconds, place: PlaceKey?): Milliseconds {
            if (requested.interval > 0) return requested
            mindboxLogE(
                "[EmbeddedBlock] Block for place '$place' was given timeout " +
                    "${requested.interval}ms: it must be positive, using the default " +
                    "${Constants.Embedded.defaultConfigTimeout.interval}ms"
            )
            return Constants.Embedded.defaultConfigTimeout
        }
    }
}
