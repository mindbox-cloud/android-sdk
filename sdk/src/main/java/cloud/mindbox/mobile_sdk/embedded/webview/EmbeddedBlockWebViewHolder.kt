package cloud.mindbox.mobile_sdk.embedded.webview

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.embedded.EmbeddedBlockState
import cloud.mindbox.mobile_sdk.fromJson
import cloud.mindbox.mobile_sdk.getOrNull
import cloud.mindbox.mobile_sdk.gatedTags
import cloud.mindbox.mobile_sdk.inapp.data.managers.SEND_INAPP_TAGS_FEATURE
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.data.validators.BridgeMessageValidator
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggleManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFailureTracker
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.presentation.view.BridgeMessage
import cloud.mindbox.mobile_sdk.inapp.presentation.view.DataCollector
import cloud.mindbox.mobile_sdk.inapp.presentation.view.InAppInsets
import cloud.mindbox.mobile_sdk.inapp.presentation.view.WebViewAction
import cloud.mindbox.mobile_sdk.inapp.presentation.view.WebViewActionHandlers
import cloud.mindbox.mobile_sdk.inapp.presentation.view.WebViewLocalStateStore
import cloud.mindbox.mobile_sdk.inapp.presentation.view.WebViewNoCacheRetryPolicy
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppWebViewCachePolicy
import cloud.mindbox.mobile_sdk.inapp.webview.*
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.models.getShortUserAgent
import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason
import cloud.mindbox.mobile_sdk.utils.Constants
import cloud.mindbox.mobile_sdk.utils.loggingRunCatchingSuspending
import cloud.mindbox.mobile_sdk.inapp.domain.extensions.sendFailureWithContext
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

@OptIn(InternalMindboxApi::class)
internal class EmbeddedBlockWebViewHolder(
    private val inAppId: String,
    @Volatile private var layer: Layer.WebViewLayer,
    private val context: Context,
) : EmbeddedUpdatableContentProvider {

    override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null

    override val contentView: android.view.View?
        get() = if (lastState == EmbeddedBlockState.Ready) webViewController?.view else null

    @Volatile private var webViewController: WebViewController? = null

    @Volatile private var isActive = false

    @Volatile private var isReleased = false

    @Volatile private var isLoadRequested = false

    @Volatile private var lastState: EmbeddedBlockState = EmbeddedBlockState.Loading

    @Volatile private var lastLoadedContent: WebViewHtmlContent? = null

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
    private val messageValidator: BridgeMessageValidator by lazy { BridgeMessageValidator() }
    private val localStateStore: WebViewLocalStateStore by lazy { WebViewLocalStateStore(appContext) }

    private val noCacheRetryPolicy: WebViewNoCacheRetryPolicy = WebViewNoCacheRetryPolicy {
        webViewCachePolicy.isCacheEnabled
    }

    @Volatile private var hasPageAnswered = false

    @Volatile private var contentLoadStartedAt: Timestamp? = null

    @Volatile private var didAccountForShow = false

    override fun start() {
        if (isReleased) return
        isActive = true
        if (!isLoadRequested) {
            isLoadRequested = true
            load()
            return
        }
        report(lastState)
    }

    override fun pause() {
        isActive = false
    }

    override fun release() {
        isActive = false
        isReleased = true
        cancelPendingResponses("Embedded block content is released")
        webViewController?.let { controller ->
            controller.setEventListener(null)
            (controller.view.parent as? ViewGroup)?.removeView(controller.view)
            controller.destroy()
        }
        webViewController = null
    }

    override fun updateParams(params: Map<String, String>, onResult: (Boolean) -> Unit) {
        val controller = webViewController ?: run {
            onResult(false)
            return
        }
        layer = layer.copy(params = params)
        val payload = buildParamsPayload(params)
        Mindbox.mindboxScope.launch {
            val isUpdated = runCatching {
                withTimeoutOrNull(Constants.WebView.readyTimeout.interval) {
                    sendActionAndAwaitResponse(
                        controller,
                        BridgeMessage.createAction(WebViewAction.INIT_DATA_UPDATED, payload)
                    )
                }
            }.getOrNull() != null
            mindboxLogI("[EmbeddedBlock] initDataUpdated for $inAppId answered success=$isUpdated")
            onResult(isUpdated)
        }
    }

    private fun load() {
        report(EmbeddedBlockState.Loading)
        val controller = runCatching { createWebViewController() }.getOrElse { error ->
            reportLoadFailure("Failed to create a WebView for the embedded block", error)
            return
        }
        webViewController = controller

        Mindbox.mindboxScope.launch {
            val configuration: Configuration = DbManager.listenConfigurations().first()
            val handlers = createActionHandlers(configuration)

            controller.setJsBridge(bridge = { json ->
                mindboxLogI("SDK <- receive message $json")
                val message = gson.fromJson<BridgeMessage>(json).getOrNull()
                if (!messageValidator.isValid(message)) {
                    return@setJsBridge
                }
                controller.executeOnViewThread {
                    if (isReleased) return@executeOnViewThread
                    when (message) {
                        is BridgeMessage.Request -> handleRequest(message, controller, handlers)
                        is BridgeMessage.Response -> handleResponse(message)
                        is BridgeMessage.Error -> handleError(message)
                        else -> mindboxLogW("Unknown message type: $message")
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
                onContentPageLoaded(WebViewHtmlContent(baseUrl = layer.baseUrl ?: "", html = response))
            }.onFailure { error ->
                // A cancelled scope is teardown, not a page failure — no telemetry, no Failed.
                if (error is CancellationException) throw error
                reportLoadFailure("Failed to fetch HTML content for the embedded block", error)
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
                    inAppFailureTracker.sendFailureWithContext(
                        inAppId = inAppId,
                        failureReason = FailureReason.WEBVIEW_PRESENTATION_FAILED,
                        errorDescription = "Embedded block WebView error: code=${error.code}, " +
                            "description=${error.description}, url=${error.url}",
                        tags = null
                    )
                    report(EmbeddedBlockState.Failed)
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
            registerSuspend(WebViewAction.READY) { handleReadyAction(configuration) }
            register(WebViewAction.INIT) {
                hasPageAnswered = true
                BridgeMessage.EMPTY_PAYLOAD
            }
            register(WebViewAction.LOG) { message ->
                mindboxLogI("JS: ${message.payload}")
                BridgeMessage.EMPTY_PAYLOAD
            }
            register(WebViewAction.CONTENT_RENDERED, ::handleContentRenderedAction)
            register(WebViewAction.SHOW_IN_APP, ::handleShowInAppAction)
            registerSuspend(WebViewAction.CHECK_INAPPS_TARGETING, ::handleCheckInappsTargetingAction)
            registerSuspend(WebViewAction.LOCAL_STATE_GET) { message ->
                localStateStore.getState(message.payload ?: BridgeMessage.EMPTY_PAYLOAD)
            }
            registerSuspend(WebViewAction.LOCAL_STATE_SET) { message ->
                localStateStore.setState(message.payload ?: BridgeMessage.EMPTY_PAYLOAD)
            }
            registerSuspend(WebViewAction.LOCAL_STATE_INIT) { message ->
                localStateStore.initState(message.payload ?: BridgeMessage.EMPTY_PAYLOAD)
            }
        }
    }

    private fun handleReadyAction(configuration: Configuration): String {
        hasPageAnswered = true
        return DataCollector(
            appContext = appContext,
            sessionStorageManager = sessionStorageManager,
            permissionManager = permissionManager,
            gson = gson,
            configuration = configuration,
            params = layer.params,
            inAppInsets = InAppInsets(),
            inAppId = inAppId,
        ).get()
    }

    private suspend fun handleCheckInappsTargetingAction(message: BridgeMessage.Request): String {
        val requestedIds = runCatching {
            gson.fromJson(message.payload, InAppIdsPayload::class.java).inappIds.orEmpty()
        }.getOrDefault(emptyList())
        val showableIds = inAppInteractor.filterShowableInAppIds(requestedIds)
        mindboxLogI(
            "[EmbeddedBlock] checkInappsTargeting: ${requestedIds.size} id(s) asked, " +
                "${showableIds.size} allowed"
        )
        return gson.toJson(InAppIdsPayload(showableIds))
    }

    private fun handleContentRenderedAction(message: BridgeMessage.Request): String {
        hasPageAnswered = true
        val count = runCatching {
            JSONObject(message.payload ?: BridgeMessage.EMPTY_PAYLOAD).getInt("count")
        }.getOrNull() ?: run {
            mindboxLogE("[EmbeddedBlock] contentRendered without a readable count, treating as broken")
            inAppFailureTracker.sendFailureWithContext(
                inAppId = inAppId,
                failureReason = FailureReason.PRESENTATION_FAILED,
                errorDescription = "The embedded block page reported contentRendered without a readable count",
                tags = null
            )
            report(EmbeddedBlockState.Failed)
            return BridgeMessage.EMPTY_PAYLOAD
        }
        mindboxLogI("[EmbeddedBlock] Page rendered $count feed element(s)")
        if (count <= 0) {
            report(EmbeddedBlockState.Empty)
            return BridgeMessage.EMPTY_PAYLOAD
        }
        report(EmbeddedBlockState.Ready)
        accountForShow()
        return BridgeMessage.EMPTY_PAYLOAD
    }

    /**
     * The block drew something, so its in-app was shown. Reported once per content instance; the
     * once-per-session rule lives in the interactor, where the session state is.
     */
    private fun accountForShow() {
        if (didAccountForShow) return
        didAccountForShow = true
        val timeToDisplay = contentLoadStartedAt
            ?.let { start -> timeProvider.elapsedSince(start) }
            ?: Milliseconds(0L)
        Mindbox.mindboxScope.launch {
            loggingRunCatchingSuspending {
                inAppInteractor.recordBlockShow(inAppId, timeToDisplay, gatedTags())
            }
        }
    }

    private fun handleShowInAppAction(message: BridgeMessage.Request): String {
        mindboxLogI("[EmbeddedBlock] Circle tap received: ${message.payload}. The show ships with the JS bridge task")
        return BridgeMessage.EMPTY_PAYLOAD
    }

    private fun onContentPageLoaded(content: WebViewHtmlContent) {
        val controller = webViewController ?: run {
            mindboxLogW("[EmbeddedBlock] WebView controller is null when loading content, skipping")
            return
        }
        lastLoadedContent = content
        hasPageAnswered = false
        contentLoadStartedAt = timeProvider.currentTimestamp()
        controller.loadContent(content)
    }

    private fun retryContentPageWithoutCache() {
        val controller = webViewController ?: return
        val content = lastLoadedContent ?: return
        mindboxLogI(
            "[EmbeddedBlock] Retrying feed content load with cache bypassed " +
                "(${noCacheRetryPolicy.lastHttpErrorDetail})"
        )
        controller.setCacheBypass(true)
        onContentPageLoaded(content)
    }

    private fun reportLoadFailure(description: String, throwable: Throwable?) {
        inAppFailureTracker.sendFailureWithContext(
            inAppId = inAppId,
            failureReason = FailureReason.WEBVIEW_LOAD_FAILED,
            errorDescription = description,
            throwable = throwable,
            tags = null
        )
        webViewController?.executeOnViewThread { report(EmbeddedBlockState.Failed) }
            ?: mainHandler.post { if (!isReleased) report(EmbeddedBlockState.Failed) }
    }

    private fun report(state: EmbeddedBlockState) {
        lastState = state
        if (isActive) onStateChange?.invoke(state)
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
        onError: ((String?) -> Unit)? = null,
    ) {
        mindboxLogI("SDK -> send message $message")
        val json: String = gson.toJson(message)
        val escapedJson: String = JSONObject.quote(json)
        controller.evaluateJavaScript(JS_CALL_BRIDGE.format(escapedJson)) { result ->
            if (result != JS_RETURN) {
                onError?.invoke(result)
            }
        }
    }

    private fun handleRequest(
        message: BridgeMessage.Request,
        controller: WebViewController,
        handlers: WebViewActionHandlers,
    ) {
        if (handlers.hasSuspendHandler(message.action)) {
            Mindbox.mindboxScope.launch {
                val responsePayload: String = handlers.handleRequestSuspend(message)
                    .getOrElse { error ->
                        sendErrorResponse(message, error, controller)
                        return@launch
                    }
                sendSuccessResponse(message, responsePayload, controller)
            }
            return
        }
        val responsePayload: String = handlers.handleRequest(message)
            .getOrElse { error ->
                mindboxLogW("[EmbeddedBlock] Unsupported page action ${message.action}: ${error.message}")
                sendErrorResponse(message, error, controller)
                return
            }
        sendSuccessResponse(message, responsePayload, controller)
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
        error: Throwable,
        controller: WebViewController,
    ) {
        val json: String = runCatching {
            gson.toJson(ErrorPayload(error = requireNotNull(error.message)))
        }.getOrDefault(BridgeMessage.UNKNOWN_ERROR_PAYLOAD)
        mindboxLogW("[EmbeddedBlock] Error response for ${message.action}: $json")
        sendActionInternal(controller, BridgeMessage.createErrorAction(message, json))
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
        pendingResponsesById.remove(message.id)?.cancel("WebView error: ${message.payload}")
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

    /** Tags of this block's in-app, gated by the feature toggle — as the overlay path does. */
    private fun gatedTags(): Map<String, String>? = sessionStorageManager.currentSessionInApps
        .firstOrNull { inApp -> inApp.id == inAppId }
        ?.gatedTags(featureToggleManager.isEnabled(SEND_INAPP_TAGS_FEATURE))

    private fun buildParamsPayload(params: Map<String, String>): String {
        val payload = JsonObject()
        params.forEach { (key, value) ->
            DataCollector.Provider.jsonStructureOrString(value).get()?.let { element ->
                payload.add(key, element)
            }
        }
        return gson.toJson(payload)
    }

    private data class InAppIdsPayload(
        @SerializedName("inappIds")
        val inappIds: List<String>?,
    )

    private data class ErrorPayload(
        @SerializedName("error")
        val error: String,
    )

    private companion object {
        private const val SHOW_IN_APP_ID_FIELD = "inappId"
        private const val JS_RETURN = "true"
        private const val JS_BRIDGE = "window.bridgeMessagesHandlers.emit"
        private const val JS_CALL_BRIDGE = "(()=>{try{$JS_BRIDGE(%s);return!0}catch(_){return!1}})()"
    }
}
