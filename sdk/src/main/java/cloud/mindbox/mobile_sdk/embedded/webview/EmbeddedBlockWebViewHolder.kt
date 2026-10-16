package cloud.mindbox.mobile_sdk.embedded.webview

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.MainThread
import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.embedded.EmbeddedBlockState
import cloud.mindbox.mobile_sdk.embedded.EmbeddedBlockStopwatch
import cloud.mindbox.mobile_sdk.findActivity
import cloud.mindbox.mobile_sdk.gatedTags
import cloud.mindbox.mobile_sdk.models.PlaceKey
import cloud.mindbox.mobile_sdk.inapp.data.managers.SEND_INAPP_TAGS_FEATURE
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.data.validators.BridgeMessageValidator
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggleManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFailureTracker
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.presentation.view.BridgeErrorCode
import cloud.mindbox.mobile_sdk.inapp.presentation.view.BridgeMessage
import cloud.mindbox.mobile_sdk.inapp.presentation.view.BridgeRefusalException
import cloud.mindbox.mobile_sdk.inapp.presentation.view.DataCollector
import cloud.mindbox.mobile_sdk.inapp.presentation.view.InAppInsets
import cloud.mindbox.mobile_sdk.inapp.presentation.view.MindboxWebPage
import cloud.mindbox.mobile_sdk.inapp.presentation.view.MindboxWebPageRegistry
import cloud.mindbox.mobile_sdk.inapp.presentation.view.WebViewAction
import cloud.mindbox.mobile_sdk.inapp.presentation.view.WebViewActionHandlers
import cloud.mindbox.mobile_sdk.inapp.presentation.view.WebViewBridgeHost
import cloud.mindbox.mobile_sdk.inapp.presentation.view.WebViewCommonBridgeActions
import cloud.mindbox.mobile_sdk.inapp.presentation.view.WebViewNoCacheRetryPolicy
import cloud.mindbox.mobile_sdk.inapp.presentation.view.dispatch
import cloud.mindbox.mobile_sdk.inapp.presentation.view.fromBridgeMessage
import cloud.mindbox.mobile_sdk.inapp.presentation.view.logBridgeRefusal
import cloud.mindbox.mobile_sdk.inapp.presentation.view.toBridgeErrorPayload
import cloud.mindbox.mobile_sdk.inapp.presentation.view.toBridgeJson
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppWebViewCachePolicy
import cloud.mindbox.mobile_sdk.inapp.webview.*
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.SessionEpoch
import cloud.mindbox.mobile_sdk.models.getShortUserAgent
import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason
import cloud.mindbox.mobile_sdk.utils.Constants
import cloud.mindbox.mobile_sdk.utils.loggingRunCatchingSuspending
import cloud.mindbox.mobile_sdk.inapp.domain.extensions.sendBlockFailureWithContext
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

@OptIn(InternalMindboxApi::class)
internal class EmbeddedBlockWebViewHolder(
    private val inAppId: String,
    private val placeSystemName: PlaceKey,
    @Volatile private var layer: Layer.WebViewLayer,
    private val context: Context,
    @Volatile private var frequency: Frequency,
    @Volatile private var tags: Map<String, String>?,
    private var selectionTime: Milliseconds,
    private val ackBudget: Milliseconds = Constants.WebView.readyTimeout,
) : EmbeddedUpdatableContentProvider, MindboxWebPage {

    override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null

    override val contentView: android.view.View?
        get() = if (lastState == EmbeddedBlockState.Ready) webViewController?.view else null

    @Volatile private var webViewController: WebViewController? = null

    private val presence = MutableStateFlow(false)

    private val isActive: Boolean get() = presence.value

    @Volatile private var isReleased = false

    @Volatile private var isLoadRequested = false

    @Volatile private var lastState: EmbeddedBlockState = EmbeddedBlockState.Loading

    override val isAttemptAlive: Boolean
        get() = lastState == EmbeddedBlockState.Loading || lastState == EmbeddedBlockState.Ready

    private var lastLoadedContent: WebViewHtmlContent? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val pendingResponsesById: MutableMap<String, CompletableDeferred<BridgeMessage.Response>> =
        ConcurrentHashMap()

    private val gson: Gson by mindboxInject { this.gson }
    private val gatewayManager: GatewayManager by mindboxInject { gatewayManager }
    private val sessionStorageManager: SessionStorageManager by mindboxInject { sessionStorageManager }
    private val permissionManager: PermissionManager by mindboxInject { permissionManager }
    private val inAppFailureTracker: InAppFailureTracker by mindboxInject { inAppFailureTracker }
    private val inAppInteractor: InAppInteractor by mindboxInject { inAppInteractor }
    private val webViewCachePolicy: InAppWebViewCachePolicy by mindboxInject { webViewCachePolicy }
    private val appContext by mindboxInject { appContext }
    private val timeProvider by mindboxInject { timeProvider }
    private val featureToggleManager: FeatureToggleManager by mindboxInject { featureToggleManager }
    private val webPageRegistry: MindboxWebPageRegistry by mindboxInject { webPageRegistry }
    private val messageValidator: BridgeMessageValidator by lazy { BridgeMessageValidator() }

    private val commonBridgeActionsLazy = lazy {
        WebViewCommonBridgeActions(object : WebViewBridgeHost {
            override val hostActivity: Activity? get() = webViewController?.view?.context?.findActivity()
            override val hostTags: Map<String, String>? get() = gatedTags()
            override val hostPage: MindboxWebPage get() = this@EmbeddedBlockWebViewHolder

            override val hostInAppId: String get() = inAppId
            override val isRequesterActive: Boolean
                get() = this@EmbeddedBlockWebViewHolder.isUserPresent && hostActivity?.isFinishing != true

            override fun sendToPage(message: BridgeMessage.Request, onError: (String?) -> Unit) {
                val controller = webViewController ?: return
                sendActionInternal(controller, message, onError = onError)
            }

            override val closeCapability: ((BridgeMessage.Request) -> String)? = null
            override val hideCapability: (() -> String)? = null

            override fun requireCanShowInApp() {
                if (lastState != EmbeddedBlockState.Loading && lastState != EmbeddedBlockState.Ready) {
                    mindboxLogI("[EmbeddedBlock] Refused a show request from a block that is not shown")
                    throw BridgeRefusalException(BridgeErrorCode.NOT_VISIBLE, "The block is not shown: $lastState")
                }
            }
        })
    }
    private val commonBridgeActions: WebViewCommonBridgeActions by commonBridgeActionsLazy

    @Volatile private var isRegisteredForBroadcasts = false

    private val noCacheRetryPolicy: WebViewNoCacheRetryPolicy = WebViewNoCacheRetryPolicy {
        webViewCachePolicy.isCacheEnabled
    }

    private var hasPageAnswered = false

    private var confirmedSessionEpoch: SessionEpoch? = null

    private var accountedSessionEpoch: SessionEpoch? = null

    private var isSessionRefreshDue = false

    private var isRenderAwaitedForSession = false

    private var isShowWithheld = false

    override var rendersNothing = false
        private set

    override var onRendersNothing: (() -> Unit)? = null

    @Volatile private var didReportShownContent = false

    private val isUserPresent: Boolean get() = isActive && !isReleased

    private val heldFailure = AtomicReference<HeldFailure?>(null)

    private data class HeldFailure(
        val failureReason: FailureReason,
        val errorDescription: String,
        val throwable: Throwable?,
        val tags: Map<String, String>?,
    )

    @Volatile private var renderedTimeToDisplay: Milliseconds? = null

    private var onScreenTime = EmbeddedBlockStopwatch(now = { timeProvider.monotonicMillis() })

    private val currentTimeToDisplay: Milliseconds
        get() = Milliseconds(selectionTime.interval + onScreenTime.elapsed.interval)

    @Volatile private var pendingAckJob: Job? = null

    private val pageScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + Mindbox.coroutineExceptionHandler)

    override fun start() {
        if (isReleased) return
        presence.value = true
        onScreenTime.resume()
        flushHeldFailure()
        if (!isLoadRequested) {
            isLoadRequested = true
            load()
            return
        }
        report(lastState)
        if (lastState == EmbeddedBlockState.Ready) accountForShow()
    }

    override fun pause() {
        presence.value = false
        onScreenTime.pause()
    }

    override fun release() {
        presence.value = false
        isReleased = true
        pendingAckJob?.cancel()
        pageScope.cancel()
        unregisterFromBroadcasts()
        if (commonBridgeActionsLazy.isInitialized()) {
            commonBridgeActions.tearDown()
        }
        heldFailure.set(null)
        lastLoadedContent = null
        cancelPendingResponses("Embedded block content is released")
        webViewController?.let { controller ->
            controller.setEventListener(null)
            (controller.view.parent as? ViewGroup)?.removeView(controller.view)
            controller.destroy()
        }
        webViewController = null
    }

    override fun refreshMetricsSnapshot(frequency: Frequency, tags: Map<String, String>?) {
        this.frequency = frequency
        this.tags = tags
    }

    @MainThread
    override fun scheduleSessionRefresh(sessionEpoch: SessionEpoch, selectionTime: Milliseconds) {
        if (!isLaterSession(sessionEpoch)) return
        confirmedSessionEpoch = sessionEpoch
        restartShowTiming(selectionTime)
        isSessionRefreshDue = true
    }

    @MainThread
    override fun confirmForSession(sessionEpoch: SessionEpoch) {
        confirmedSessionEpoch = sessionEpoch
    }

    @MainThread
    override fun timeToDisplaySoFar(sessionEpoch: SessionEpoch): Milliseconds? =
        currentTimeToDisplay.takeIf { isConfirmedAndUnaccounted(sessionEpoch) }

    @MainThread
    override fun withholdShow(isWithheld: Boolean) {
        if (isShowWithheld == isWithheld) return
        isShowWithheld = isWithheld
        if (!isWithheld && lastState == EmbeddedBlockState.Ready && isActive) accountForShow()
    }

    @MainThread
    override fun refreshForSession(
        params: Map<String, String>,
        sessionEpoch: SessionEpoch,
        selectionTime: Milliseconds,
        onResult: (DataPushOutcome) -> Unit,
    ) {
        if (webViewController != null) {
            if (isLaterSession(sessionEpoch)) {
                confirmedSessionEpoch = sessionEpoch
                restartShowTiming(selectionTime)
            }
            if (isConfirmedAndUnaccounted(sessionEpoch)) {
                isSessionRefreshDue = false
                isRenderAwaitedForSession = true
            }
        }
        updateParams(params, onResult)
    }

    private fun isLaterSession(sessionEpoch: SessionEpoch): Boolean =
        confirmedSessionEpoch?.let { confirmed -> sessionEpoch > confirmed } ?: true

    private fun isConfirmedAndUnaccounted(sessionEpoch: SessionEpoch): Boolean =
        sessionEpoch == confirmedSessionEpoch && sessionEpoch != accountedSessionEpoch

    private fun restartShowTiming(selectionTime: Milliseconds) {
        this.selectionTime = selectionTime
        renderedTimeToDisplay = null
        onScreenTime = EmbeddedBlockStopwatch(now = { timeProvider.monotonicMillis() }).also { stopwatch ->
            if (isActive) stopwatch.resume()
        }
    }

    override val isDataPushAbandoned: Boolean
        get() = pendingAckJob?.isCancelled == true

    override fun updateParams(params: Map<String, String>, onResult: (DataPushOutcome) -> Unit) {
        pendingAckJob?.cancel()
        val controller = webViewController ?: run {
            pendingAckJob = null
            onResult(DataPushOutcome.REFUSED)
            return
        }
        layer = layer.copy(params = params)
        didReportShownContent = false
        var outcome = DataPushOutcome.REFUSED
        pendingAckJob = Mindbox.mindboxScope.launch {
            val configuration: Configuration = DbManager.listenConfigurations().first()
            val payload = startPayload(configuration)
            val isUpdated = runCatching {
                coroutineScope {
                    val answer = async {
                        sendActionAndAwaitResponse(
                            controller,
                            BridgeMessage.createAction(WebViewAction.INIT_DATA_UPDATED, payload)
                        )
                    }
                    val response = answer.awaitWithForegroundBudget(ackBudget)
                    if (response == null) answer.cancel()
                    response
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
            }.getOrNull() != null
            if (isUpdated) {
                mindboxLogI("[EmbeddedBlock] Block '$placeSystemName': the page took the new data — waiting for contentRendered")
            } else {
                mindboxLogI("[EmbeddedBlock] initDataUpdated for $inAppId answered success=false")
            }
            outcome = if (isUpdated) DataPushOutcome.CONFIRMED else DataPushOutcome.REFUSED
        }.apply {
            invokeOnCompletion { cause ->
                if (cause is CancellationException) {
                    mindboxLogI("[EmbeddedBlock] initDataUpdated for $inAppId was abandoned before the page answered it")
                    onResult(DataPushOutcome.ABANDONED)
                } else {
                    onResult(outcome)
                }
            }
        }
    }

    private fun load() {
        report(EmbeddedBlockState.Loading)
        val controller = runCatching { createWebViewController() }.getOrElse { error ->
            reportLoadFailure("Failed to create a WebView for the embedded block", error)
            return
        }
        webViewController = controller

        pageScope.launch {
            val configuration: Configuration = DbManager.listenConfigurations().first()
            val handlers = createActionHandlers(configuration)

            controller.setJsBridge(bridge = { json ->
                mindboxLogI("SDK <- receive message $json")
                val (message, sentAction) = gson.fromBridgeMessage(json) ?: return@setJsBridge
                if (!messageValidator.isValid(message)) {
                    return@setJsBridge
                }
                controller.executeOnViewThread {
                    if (isReleased) return@executeOnViewThread
                    when (message) {
                        is BridgeMessage.Request -> handleRequest(message, sentAction, controller, handlers)
                        is BridgeMessage.Response -> handleResponse(message)
                        is BridgeMessage.Error -> handleError(message)
                    }
                }
            })
            controller.setUserAgentSuffix(configuration.getShortUserAgent())

            val contentUrl = layer.contentUrl ?: run {
                reportLoadFailure("WebView content URL is null for the embedded block", throwable = null)
                return@launch
            }
            runCatching {
                gatewayManager.fetchWebViewContent(contentUrl)
            }.onSuccess { response: String ->
                controller.executeOnViewThread {
                    if (isReleased) return@executeOnViewThread
                    onContentPageLoaded(WebViewHtmlContent(baseUrl = layer.baseUrl ?: "", html = response))
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                reportLoadFailure("Failed to fetch page HTML", error)
            }
        }
    }

    private fun createWebViewController(): WebViewController {
        mindboxLogI("[EmbeddedBlock] Creating WebView for embedded in-app $inAppId")
        val controller = WebViewController.create(
            context = context,
            isDebugEnabled = false,
            isCacheEnabled = false,
            log = { message -> mindboxLogI("[EmbeddedBlock][WebView] $message") }
        )
        controller.view.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        controller.setEventListener(object : WebViewEventListener {
            override fun onPageFinished(url: String?) {
                mindboxLogI("[EmbeddedBlock] onPageFinished: $url")
            }

            override fun onShouldOverrideUrlLoading(url: String?, isForMainFrame: Boolean?): Boolean {
                return isForMainFrame == true
            }

            override fun onError(error: WebViewError) {
                mindboxLogE(
                    "[EmbeddedBlock] WebView error: code=${error.code}, " +
                        "description=${error.description}, url=${error.url}"
                )
                if (error.isForMainFrame == true) {
                    fail(
                        FailureReason.WEBVIEW_LOAD_FAILED,
                        "Page failed to load: ${error.code} ${error.description}",
                    )
                }
            }

            override fun onHttpError(url: String?, statusCode: Int?, isForMainFrame: Boolean?) {
                mindboxLogI("[EmbeddedBlock] HTTP error $statusCode for $url (mainFrame=$isForMainFrame)")
                if (noCacheRetryPolicy.onHttpError(url, statusCode, hasPageAnswered)) {
                    retryContentPageWithoutCache()
                }
            }
        })
        return controller
    }

    private fun createActionHandlers(configuration: Configuration): WebViewActionHandlers {
        return WebViewActionHandlers().apply {
            commonBridgeActions.register(this)
            register(WebViewAction.READY) { handleReadyAction(configuration) }
            register(WebViewAction.INIT) {
                onPageAnswered()
                BridgeMessage.SUCCESS_PAYLOAD
            }
            register(WebViewAction.CONTENT_RENDERED, ::handleContentRenderedAction)
        }
    }

    private fun handleReadyAction(configuration: Configuration): String {
        onPageAnswered()
        registerForBroadcasts()
        return startPayload(configuration)
    }

    private fun onPageAnswered() {
        hasPageAnswered = true
        lastLoadedContent = null
    }

    private fun registerForBroadcasts() {
        if (isReleased) return
        if (isRegisteredForBroadcasts) return
        isRegisteredForBroadcasts = true
        webPageRegistry.register(this)
    }

    private fun unregisterFromBroadcasts() {
        if (!isRegisteredForBroadcasts) return
        isRegisteredForBroadcasts = false
        webPageRegistry.unregister(this)
    }

    override fun push(action: WebViewAction, payload: String) {
        if (isReleased) return
        val controller = webViewController ?: return
        Mindbox.mindboxScope.launch {
            loggingRunCatchingSuspending {
                val response = withTimeoutOrNull(Constants.WebView.readyTimeout.interval) {
                    sendActionAndAwaitResponse(controller, BridgeMessage.createAction(action, payload))
                }
                mindboxLogI(
                    "[EmbeddedBlock] push '$action' " +
                        (if (response != null) "confirmed" else "was not confirmed") + " by the page"
                )
            }
        }
    }

    private fun startPayload(configuration: Configuration): String = DataCollector(
        appContext = appContext,
        sessionStorageManager = sessionStorageManager,
        permissionManager = permissionManager,
        gson = gson,
        configuration = configuration,
        params = DataCollector.mergedParams(config = layer.params),
        inAppInsets = InAppInsets(),
        inAppId = inAppId,
        operation = null,
    ).get()

    @MainThread
    private fun handleContentRenderedAction(message: BridgeMessage.Request): String {
        onPageAnswered()
        if (didReportShownContent) {
            mindboxLogI("[EmbeddedBlock] The page reported itself again with nothing asked of it, ignoring: the block is already shown")
            return BridgeMessage.SUCCESS_PAYLOAD
        }
        val raw: Any? = runCatching {
            JSONObject(message.payload ?: BridgeMessage.EMPTY_PAYLOAD).get("count")
        }.getOrNull()
        val number = raw as? Number
            ?: throw refusedContentReport("missing or non-numeric 'count'")
        val count = number.toWholeIntOrNull()
            ?: throw refusedContentReport("'count' must be a whole number of items, got $number")
        if (count < 0) throw refusedContentReport("'count' must not be negative, got $count")
        mindboxLogI("[EmbeddedBlock] Page rendered $count content element(s)")
        if (count == 0) {
            if (lastState == EmbeddedBlockState.Ready) {
                mindboxLogI("[EmbeddedBlock] The shown page has nothing to show any more, its block decides when to collapse")
                rendersNothing = true
                onRendersNothing?.invoke()
            } else {
                report(EmbeddedBlockState.Empty)
            }
            return BridgeMessage.SUCCESS_PAYLOAD
        }
        rendersNothing = false
        didReportShownContent = true
        if (!isSessionRefreshDue) {
            isRenderAwaitedForSession = false
            renderedTimeToDisplay = renderedTimeToDisplay ?: currentTimeToDisplay
        }
        report(EmbeddedBlockState.Ready)
        if (isActive) accountForShow() else mindboxLogI("[EmbeddedBlock] Rendered off screen, shown when back on screen")
        return BridgeMessage.SUCCESS_PAYLOAD
    }

    private fun Number.toWholeIntOrNull(): Int? = when (this) {
        is Int -> this
        is Double -> toInt().takeIf { whole -> whole.toDouble() == this }
        else -> null
    }

    private fun refusedContentReport(reason: String): Throwable {
        mindboxLogE("[EmbeddedBlock] contentRendered refused: $reason")
        fail(FailureReason.PRESENTATION_FAILED, "Page sent contentRendered with an invalid count")
        return BridgeRefusalException(BridgeErrorCode.INVALID_PAYLOAD, reason)
    }

    private fun fail(reason: FailureReason, description: String) {
        sendFailure(failureReason = reason, errorDescription = description, tags = gatedTags())
        report(EmbeddedBlockState.Failed(reason))
    }

    @MainThread
    private fun accountForShow() {
        val sessionEpoch = confirmedSessionEpoch ?: return
        if (!isConfirmedAndUnaccounted(sessionEpoch) || isSessionRefreshDue || isRenderAwaitedForSession) return
        if (isShowWithheld) {
            mindboxLogI("[EmbeddedBlock] Its block withholds the show of this content until the place's next content applies")
            return
        }
        if (rendersNothing) {
            mindboxLogI("[EmbeddedBlock] The page has nothing to show any more, its show waits until it renders content")
            return
        }
        if (!isUserPresent) {
            mindboxLogI("[EmbeddedBlock] Content rendered off screen, the show waits for the block to return")
            return
        }
        val previouslyAccounted = accountedSessionEpoch
        accountedSessionEpoch = sessionEpoch
        val timeToDisplay = renderedTimeToDisplay ?: currentTimeToDisplay
        mindboxLogI("[EmbeddedBlock] Block '$placeSystemName': in-app $inAppId is shown in session ${sessionEpoch.number}, timeToDisplay=${timeToDisplay.interval} ms")
        val shownFrequency = frequency
        val shownTags = gatedTags()
        Mindbox.mindboxScope.launch {
            loggingRunCatchingSuspending {
                if (inAppInteractor.returnCheckPending.value) {
                    inAppInteractor.awaitReturnChecked()
                    if (!isShowStillDue(sessionEpoch, previouslyAccounted)) return@loggingRunCatchingSuspending
                }
                inAppInteractor.recordBlockShow(placeSystemName, inAppId, shownFrequency, timeToDisplay, shownTags, sessionEpoch)
            }
        }.invokeOnCompletion { cause ->
            if (cause !is CancellationException) return@invokeOnCompletion
            mainHandler.post {
                rollBackAccounting(sessionEpoch, previouslyAccounted)
            }
        }
    }

    private suspend fun isShowStillDue(sessionEpoch: SessionEpoch, previouslyAccounted: SessionEpoch?): Boolean =
        withContext(Dispatchers.Main) {
            if (!isShowWithheld && !rendersNothing) return@withContext true
            mindboxLogI("[EmbeddedBlock] The show of this content was withheld or its page emptied during the session check, the show waits again")
            rollBackAccounting(sessionEpoch, previouslyAccounted)
            false
        }

    @MainThread
    private fun rollBackAccounting(sessionEpoch: SessionEpoch, previouslyAccounted: SessionEpoch?) {
        if (accountedSessionEpoch == sessionEpoch) accountedSessionEpoch = previouslyAccounted
    }

    @MainThread
    private fun onContentPageLoaded(content: WebViewHtmlContent) {
        val controller = webViewController ?: run {
            mindboxLogW("[EmbeddedBlock] WebView controller is null when loading content, skipping")
            return
        }
        lastLoadedContent = content
        hasPageAnswered = false
        didReportShownContent = false
        controller.loadContent(content)
    }

    private fun retryContentPageWithoutCache() {
        val controller = webViewController ?: return
        val content = lastLoadedContent ?: return
        mindboxLogI(
            "[EmbeddedBlock] Retrying block content load with cache bypassed " +
                "(${noCacheRetryPolicy.lastHttpErrorDetail})"
        )
        controller.setCacheBypass(true)
        onContentPageLoaded(content)
    }

    private fun sendFailure(
        failureReason: FailureReason,
        errorDescription: String,
        throwable: Throwable? = null,
        tags: Map<String, String>? = null,
    ) {
        if (!isActive) {
            mindboxLogI(
                "[EmbeddedBlock] $failureReason for $inAppId happened off screen, holding the " +
                    "report until the block is looked at"
            )
            heldFailure.compareAndSet(null, HeldFailure(failureReason, errorDescription, throwable, tags))
            if (isActive) flushHeldFailure()
            return
        }
        inAppFailureTracker.sendBlockFailureWithContext(
            inAppId = inAppId,
            failureReason = failureReason,
            errorDescription = errorDescription,
            throwable = throwable,
            tags = tags
        )
    }

    private fun flushHeldFailure() {
        val held = heldFailure.getAndSet(null) ?: return
        inAppFailureTracker.sendBlockFailureWithContext(
            inAppId = inAppId,
            failureReason = held.failureReason,
            errorDescription = held.errorDescription,
            throwable = held.throwable,
            tags = held.tags
        )
    }

    private fun reportLoadFailure(description: String, throwable: Throwable?) {
        sendFailure(
            failureReason = FailureReason.WEBVIEW_LOAD_FAILED,
            errorDescription = description,
            throwable = throwable,
            tags = gatedTags()
        )
        val failed = EmbeddedBlockState.Failed(FailureReason.WEBVIEW_LOAD_FAILED)
        webViewController?.executeOnViewThread { report(failed) }
            ?: mainHandler.post { if (!isReleased) report(failed) }
    }

    private fun report(state: EmbeddedBlockState) {
        lastState = state
        if (isActive) onStateChange?.invoke(state)
    }

    private suspend fun <T> Deferred<T>.awaitWithForegroundBudget(budget: Milliseconds): T? {
        var remaining = budget
        while (true) {
            if (!presence.value) {
                if (settlesBeforePresenceTurns(lookedAt = true)) return await()
                mindboxLogI(
                    "[EmbeddedBlock] Back on screen with a data push still unconfirmed — " +
                        "waiting out the remaining ${remaining.interval}ms"
                )
                continue
            }
            if (remaining.interval <= 0L) return null
            val spendStartedTick = timeProvider.monotonicMillis()
            when (withTimeoutOrNull(remaining.interval) { settlesBeforePresenceTurns(lookedAt = false) }) {
                null -> return null
                true -> return await()
                false -> remaining = Milliseconds(
                    (remaining.interval - timeProvider.monotonicElapsedSince(spendStartedTick).interval)
                        .coerceAtLeast(0L)
                )
            }
        }
    }

    private suspend fun Job.settlesBeforePresenceTurns(lookedAt: Boolean): Boolean = coroutineScope {
        val presenceTurned = async { presence.first { isLookedAt -> isLookedAt == lookedAt } }
        try {
            select {
                this@settlesBeforePresenceTurns.onJoin { true }
                presenceTurned.onJoin { false }
            }
        } finally {
            presenceTurned.cancel()
        }
    }

    private suspend fun sendActionAndAwaitResponse(
        controller: WebViewController,
        message: BridgeMessage.Request,
    ): BridgeMessage.Response {
        val responseDeferred: CompletableDeferred<BridgeMessage.Response> = CompletableDeferred()
        pendingResponsesById[message.id] = responseDeferred
        sendActionInternal(controller, message) { error ->
            if (responseDeferred.isActive) {
                responseDeferred.completeExceptionally(
                    IllegalStateException("Failed to send message ${message.action} to WebView: $error")
                )
            }
        }
        return try {
            responseDeferred.await()
        } finally {
            pendingResponsesById.remove(message.id)
        }
    }

    private fun sendActionInternal(
        controller: WebViewController,
        message: BridgeMessage,
        sentAction: String? = null,
        onError: ((String?) -> Unit)? = null,
    ) {
        mindboxLogI("SDK -> send message $message")
        val json: String = gson.toBridgeJson(message, sentAction)
        val escapedJson: String = JSONObject.quote(json)
        controller.evaluateJavaScript(JS_CALL_BRIDGE.format(escapedJson)) { result ->
            if (result != JS_RETURN) {
                onError?.invoke(result)
            }
        }
    }

    private fun handleRequest(
        message: BridgeMessage.Request,
        sentAction: String?,
        controller: WebViewController,
        handlers: WebViewActionHandlers,
    ) {
        handlers.dispatch(
            message = message,
            isUserPresent = isUserPresent,
            isAlive = { !isReleased },
            launchSuspending = { handle -> Mindbox.mindboxScope.launch { handle() } },
            respond = { payload -> sendSuccessResponse(message, payload, controller) },
            refuse = { error -> sendErrorResponse(message, sentAction, error, controller) },
        )
    }

    private fun sendSuccessResponse(
        message: BridgeMessage.Request,
        responsePayload: String?,
        controller: WebViewController,
    ) {
        sendActionInternal(controller, BridgeMessage.createResponseAction(message, responsePayload))
    }

    private fun sendErrorResponse(
        message: BridgeMessage.Request,
        sentAction: String?,
        error: Throwable,
        controller: WebViewController,
    ) {
        logBridgeRefusal(message, inAppId, error)
        sendActionInternal(
            controller = controller,
            message = BridgeMessage.createErrorAction(message, gson.toBridgeErrorPayload(error)),
            sentAction = sentAction,
        )
    }

    private fun handleResponse(message: BridgeMessage.Response) {
        val responseDeferred = pendingResponsesById.remove(message.id)
        if (responseDeferred == null) {
            mindboxLogW("No pending response for id ${message.id}")
            return
        }
        if (!responseDeferred.isCompleted) {
            responseDeferred.complete(message)
        }
    }

    private fun handleError(message: BridgeMessage.Error) {
        mindboxLogW("[EmbeddedBlock] WebView error message: ${message.payload}")
        pendingResponsesById.remove(message.id)?.completeExceptionally(IllegalStateException("WebView error: ${message.payload}"))
    }

    private fun cancelPendingResponses(reason: String) {
        val error = CancellationException(reason)
        pendingResponsesById.values.forEach { deferred ->
            if (!deferred.isCompleted) {
                deferred.cancel(error)
            }
        }
        pendingResponsesById.clear()
    }

    private fun gatedTags(): Map<String, String>? =
        tags.gatedTags(featureToggleManager.isEnabled(SEND_INAPP_TAGS_FEATURE))

    private companion object {
        private const val JS_RETURN = "true"
        private const val JS_BRIDGE = "window.bridgeMessagesHandlers.emit"
        private const val JS_CALL_BRIDGE = "(()=>{try{$JS_BRIDGE(%s);return!0}catch(_){return!1}})()"
    }
}
