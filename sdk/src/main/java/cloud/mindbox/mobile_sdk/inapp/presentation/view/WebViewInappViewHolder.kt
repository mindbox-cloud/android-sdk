package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.app.Application
import android.net.Uri
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import cloud.mindbox.mobile_sdk.BuildConfig
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.fromJson
import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.data.validators.BridgeMessageValidator
import cloud.mindbox.mobile_sdk.inapp.domain.extensions.executeWithFailureTracking
import cloud.mindbox.mobile_sdk.inapp.domain.extensions.sendFailureWithContext
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionManager
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTypeWrapper
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback
import cloud.mindbox.mobile_sdk.inapp.presentation.MindboxView
import cloud.mindbox.mobile_sdk.inapp.webview.*
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.getShortUserAgent
import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason
import cloud.mindbox.mobile_sdk.safeAs
import cloud.mindbox.mobile_sdk.utils.MindboxUtils.Stopwatch
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale
import java.util.Timer
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.timer

@OptIn(InternalMindboxApi::class)
internal class WebViewInAppViewHolder(
    override val wrapper: InAppTypeWrapper<InAppType.WebView>,
    private val inAppCallback: InAppCallback,
) : AbstractInAppViewHolder<InAppType.WebView>() {

    companion object {
        private const val INIT_TIMEOUT_MS = 7_000L
        private const val TIMER = "CLOSE_INAPP_TIMER"
        private const val JS_RETURN = "true"
        private const val JS_BRIDGE_CLASS = "window.bridgeMessagesHandlers"
        private const val JS_BRIDGE = "$JS_BRIDGE_CLASS.emit"
        private const val JS_CALL_BRIDGE = "(()=>{try{$JS_BRIDGE(%s);return!0}catch(_){return!1}})()"
        private const val JS_CHECK_BRIDGE = "(() => typeof $JS_BRIDGE_CLASS !== 'undefined' && typeof $JS_BRIDGE === 'function')()"
    }

    private var closeInappTimer: Timer? = null
    private var webViewController: WebViewController? = null
    private var backPressedCallback: OnBackPressedCallback? = null
    private var currentWebViewOrigin: String? = null
    private val pendingResponsesById: MutableMap<String, CompletableDeferred<BridgeMessage.Response>> =
        ConcurrentHashMap()

    private val gson: Gson by mindboxInject { this.gson }
    private val messageValidator: BridgeMessageValidator by lazy { BridgeMessageValidator() }
    private val gatewayManager: GatewayManager by mindboxInject { gatewayManager }
    private val sessionStorageManager: SessionStorageManager by mindboxInject { sessionStorageManager }
    private val permissionManager: PermissionManager by mindboxInject { permissionManager }
    private val appContext: Application by mindboxInject { appContext }
    private val operationExecutor: WebViewOperationExecutor by lazy {
        MindboxWebViewOperationExecutor()
    }
    private val linkRouter: WebViewLinkRouter by lazy {
        MindboxWebViewLinkRouter(appContext)
    }

    override val isActive: Boolean
        get() = isInAppMessageActive

    override fun bind() {
        inAppLayout.setDismissListener {
            inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
            mindboxLogI("In-app dismissed by dialog click")
            hide()
        }
    }

    suspend fun sendActionAndAwaitResponse(
        controller: WebViewController,
        message: BridgeMessage.Request
    ): BridgeMessage.Response {
        val responseDeferred: CompletableDeferred<BridgeMessage.Response> = CompletableDeferred()
        pendingResponsesById[message.id] = responseDeferred
        sendActionInternal(controller = controller, message = message) { error ->
            if (responseDeferred.isActive) {
                responseDeferred.completeExceptionally(
                    IllegalStateException("Failed to send message ${message.action} to WebView: $error")
                )
            }
        }
        return responseDeferred.await()
    }

    private fun sendActionInternal(
        controller: WebViewController,
        message: BridgeMessage,
        onError: ((String?) -> Unit)? = null
    ) {
        mindboxLogI("SDK -> send message $message")
        val json: String = gson.toJson(message)
        val escapedJson: String = JSONObject.quote(json)
        controller.evaluateJavaScript(JS_CALL_BRIDGE.format(escapedJson)) { result ->
            if (!checkEvaluateJavaScript(result)) {
                onError?.invoke(result)
            }
        }
    }

    private fun createWebViewActionHandlers(
        controller: WebViewController,
        layer: Layer.WebViewLayer,
        configuration: Configuration
    ): WebViewActionHandlers {
        return WebViewActionHandlers().apply {
            register(WebViewAction.CLICK, ::handleClickAction)
            register(WebViewAction.CLOSE, ::handleCloseAction)
            register(WebViewAction.LOG, ::handleLogAction)
            register(WebViewAction.TOAST, ::handleToastAction)
            register(WebViewAction.ALERT, ::handleAlertAction)
            register(WebViewAction.ASYNC_OPERATION, ::handleAsyncOperationAction)
            register(WebViewAction.OPEN_LINK, ::handleOpenLinkAction)
            registerSuspend(WebViewAction.SYNC_OPERATION, ::handleSyncOperationAction)
            register(WebViewAction.READY) {
                handleReadyAction(
                    configuration = configuration,
                    insets = inAppLayout.webViewInsets,
                    params = layer.params,
                    inAppId = wrapper.inAppType.inAppId,
                )
            }
            register(WebViewAction.INIT) {
                handleInitAction(controller)
            }
            register(WebViewAction.HIDE) {
                handleHideAction(controller)
            }
        }
    }

    private fun handleReadyAction(
        configuration: Configuration,
        insets: InAppInsets,
        params: Map<String, String>,
        inAppId: String,
    ): String {
        return DataCollector(
            appContext = appContext,
            sessionStorageManager = sessionStorageManager,
            permissionManager = permissionManager,
            gson = gson,
            configuration = configuration,
            params = params,
            inAppInsets = insets,
            inAppId = inAppId,
        ).get()
    }

    private fun handleInitAction(controller: WebViewController): String {
        stopTimer()
        wrapper.inAppActionCallbacks.onInAppShown.onShown()
        controller.setVisibility(true)
        backPressedCallback?.isEnabled = true
        return BridgeMessage.EMPTY_PAYLOAD
    }

    private fun handleClickAction(message: BridgeMessage.Request): String {
        runCatching {
            val actionDto: BackgroundDto.LayerDto.ImageLayerDto.ActionDto =
                gson.fromJson<BackgroundDto.LayerDto.ImageLayerDto.ActionDto>(message.payload).getOrThrow()
            val actionResult: Pair<String?, String?> = when (actionDto) {
                is BackgroundDto.LayerDto.ImageLayerDto.ActionDto.RedirectUrlActionDto ->
                    actionDto.value to actionDto.intentPayload

                is BackgroundDto.LayerDto.ImageLayerDto.ActionDto.PushPermissionActionDto ->
                    "" to actionDto.intentPayload
            }
            val url: String? = actionResult.first
            val payload: String? = actionResult.second
            inAppCallback.onInAppClick(
                wrapper.inAppType.inAppId,
                url ?: "",
                payload ?: ""
            )
        }
        mindboxLogI("In-app completed by webview action with data: ${message.payload}")
        return BridgeMessage.EMPTY_PAYLOAD
    }

    private fun handleCloseAction(message: BridgeMessage): String {
        inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
        mindboxLogI("In-app dismissed by webview action ${message.action} with payload ${message.payload}")
        hide()
        release()
        return BridgeMessage.EMPTY_PAYLOAD
    }

    private fun handleHideAction(controller: WebViewController): String {
        controller.setVisibility(false)
        return BridgeMessage.EMPTY_PAYLOAD
    }

    private fun handleLogAction(message: BridgeMessage.Request): String {
        mindboxLogI("JS: ${message.payload}")
        return BridgeMessage.EMPTY_PAYLOAD
    }

    private fun handleToastAction(message: BridgeMessage.Request): String {
        webViewController?.view?.context?.let { context ->
            Toast.makeText(context, message.payload, Toast.LENGTH_LONG).show()
        }
        return BridgeMessage.EMPTY_PAYLOAD
    }

    private fun handleAlertAction(message: BridgeMessage.Request): String {
        webViewController?.view?.context?.let { context ->
            AlertDialog.Builder(context)
                .setMessage(message.payload)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }
        return BridgeMessage.EMPTY_PAYLOAD
    }

    private fun handleAsyncOperationAction(message: BridgeMessage.Request): String {
        operationExecutor.executeAsyncOperation(appContext, message.payload)
        return BridgeMessage.EMPTY_PAYLOAD
    }

    private fun handleOpenLinkAction(message: BridgeMessage.Request): String {
        linkRouter.executeOpenLink(message.payload)
            .getOrElse { error: Throwable ->
                throw IllegalStateException(error.message ?: "Navigation error")
            }
        return BridgeMessage.SUCCESS_PAYLOAD
    }

    private suspend fun handleSyncOperationAction(message: BridgeMessage.Request): String {
        return operationExecutor.executeSyncOperation(message.payload)
    }

    private fun createWebViewController(layer: Layer.WebViewLayer): WebViewController {
        mindboxLogI("Creating WebView for In-App: ${wrapper.inAppType.inAppId} with layer ${layer.type}")
        val controller: WebViewController = WebViewController.create(currentDialog.context, BuildConfig.DEBUG)
        val view: WebViewPlatformView = controller.view
        view.layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        controller.setEventListener(object : WebViewEventListener {
            override fun onPageFinished(url: String?) {
                mindboxLogD("onPageFinished: $url")
                currentWebViewOrigin = resolveOrigin(url) ?: currentWebViewOrigin
                webViewController?.evaluateJavaScript(JS_CHECK_BRIDGE, ::checkEvaluateJavaScript)
            }

            override fun onShouldOverrideUrlLoading(url: String?, isForMainFrame: Boolean?): Boolean {
                return handleShouldOverrideUrlLoading(url = url, isForMainFrame = isForMainFrame)
            }

            override fun onError(error: WebViewError) {
                mindboxLogE("WebView error: code=${error.code}, description=${error.description}, url=${error.url}")
                if (error.isForMainFrame == true) {
                    inAppFailureTracker.sendFailureWithContext(
                        inAppId = wrapper.inAppType.inAppId,
                        failureReason = FailureReason.WEBVIEW_PRESENTATION_FAILED,
                        errorDescription = "WebView error: code=${error.code}, description=${error.description}, url=${error.url}"
                    )
                }
            }
        })
        return controller
    }

    private fun handleShouldOverrideUrlLoading(url: String?, isForMainFrame: Boolean?): Boolean {
        if (isForMainFrame != true) {
            return false
        }
        if (shouldAllowLocalNavigation(url)) {
            return false
        }
        val normalizedUrl: String = url?.trim().orEmpty()
        sendNavigationInterceptedEvent(url = normalizedUrl)
        return true
    }

    private fun sendNavigationInterceptedEvent(url: String) {
        val controller: WebViewController = webViewController ?: return
        val payload: String = gson.toJson(NavigationInterceptedPayload(url = url))
        val message: BridgeMessage.Request = BridgeMessage.createAction(
            action = WebViewAction.NAVIGATION_INTERCEPTED,
            payload = payload
        )
        sendActionInternal(controller, message) { error ->
            mindboxLogW("Failed to send navigationIntercepted event to WebView: $error")
        }
    }

    private fun shouldAllowLocalNavigation(url: String?): Boolean {
        if (url.isNullOrBlank()) {
            return true
        }
        val normalizedUrl: String = url.trim()
        if (normalizedUrl.startsWith("#")) {
            return true
        }
        if (normalizedUrl.startsWith("about:blank")) {
            return true
        }
        val targetOrigin: String = resolveOrigin(normalizedUrl) ?: return false
        val sourceOrigin: String = currentWebViewOrigin ?: return false
        return targetOrigin == sourceOrigin
    }

    private fun resolveOrigin(url: String?): String? {
        if (url.isNullOrBlank()) {
            return null
        }
        val parsedUri: Uri = runCatching { url.toUri() }.getOrNull() ?: return null
        val scheme: String = parsedUri.scheme?.lowercase(Locale.US).orEmpty()
        val host: String = parsedUri.host?.lowercase(Locale.US).orEmpty()
        if (scheme.isBlank() || host.isBlank()) {
            return null
        }
        val normalizedPort: String = if (parsedUri.port >= 0) ":${parsedUri.port}" else ""
        return "$scheme://$host$normalizedPort"
    }

    private fun clearBackPressedCallback() {
        backPressedCallback?.remove()
    }

    private fun sendBackAction(controller: WebViewController) {
        val message: BridgeMessage.Request = BridgeMessage.createAction(
            WebViewAction.BACK,
            BridgeMessage.EMPTY_PAYLOAD
        )
        sendActionInternal(controller, message) { error ->
            mindboxLogW("Failed to send back action to WebView: $error")
            inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
            hide()
        }
    }

    internal fun checkEvaluateJavaScript(response: String?): Boolean {
        return when (response) {
            JS_RETURN -> true
            else -> {
                inAppFailureTracker.sendFailureWithContext(
                    inAppId = wrapper.inAppType.inAppId,
                    failureReason = FailureReason.WEBVIEW_PRESENTATION_FAILED,
                    errorDescription = "evaluateJavaScript return unexpected response: $response"
                )
                hide()
                false
            }
        }
    }

    private fun handleRequest(message: BridgeMessage.Request, controller: WebViewController, handlers: WebViewActionHandlers) {
        if (handlers.hasSuspendHandler(message.action)) {
            Mindbox.mindboxScope.launch {
                val responsePayload: String = handlers.handleRequestSuspend(message)
                    .getOrElse { error ->
                        sendErrorResponse(message = message, error = error, controller = controller)
                        return@launch
                    }
                sendSuccessResponse(message = message, responsePayload = responsePayload, controller = controller)
            }
            return
        }
        val responsePayload: String = handlers.handleRequest(message)
            .getOrElse { error ->
                sendErrorResponse(message = message, error = error, controller = controller)
                return
            }
        sendSuccessResponse(message = message, responsePayload = responsePayload, controller = controller)
    }

    private fun sendSuccessResponse(
        message: BridgeMessage.Request,
        responsePayload: String?,
        controller: WebViewController,
    ) {
        val responseMessage: BridgeMessage.Response = BridgeMessage.createResponseAction(message, responsePayload)
        sendActionInternal(controller, responseMessage)
    }

    private fun sendErrorResponse(
        message: BridgeMessage.Request,
        error: Throwable,
        controller: WebViewController,
    ) {
        val json: String = runCatching {
            val payload = ErrorPayload(error = requireNotNull(error.message))
            gson.toJson(payload)
        }.getOrDefault(BridgeMessage.UNKNOWN_ERROR_PAYLOAD)

        val errorMessage: BridgeMessage.Error = BridgeMessage.createErrorAction(message, json)
        mindboxLogE("WebView send error response for ${message.action} with payload ${errorMessage.payload}")
        sendActionInternal(controller, errorMessage)
    }

    private fun handleResponse(message: BridgeMessage.Response) {
        val responseDeferred: CompletableDeferred<BridgeMessage.Response>? = pendingResponsesById.remove(message.id)
        if (responseDeferred == null) {
            mindboxLogW("No pending response for id $message.id")
            return
        }
        if (!responseDeferred.isCompleted) {
            responseDeferred.complete(message)
        }
    }

    private fun handleError(message: BridgeMessage.Error) {
        mindboxLogW("WebView error: ${message.payload}")
        val responseDeferred: CompletableDeferred<BridgeMessage.Response>? = pendingResponsesById.remove(message.id)
        responseDeferred?.cancel("WebView error: ${message.payload}")
        hide()
    }

    private fun cancelPendingResponses(reason: String) {
        val error: CancellationException = CancellationException(reason)
        pendingResponsesById.values.forEach { deferred ->
            if (!deferred.isCompleted) {
                deferred.cancel(error)
            }
        }
        pendingResponsesById.clear()
    }

    private fun renderLayer(layer: Layer.WebViewLayer) {
        if (webViewController == null) {
            val controller: WebViewController = createWebViewController(layer)
            webViewController = controller

            Mindbox.mindboxScope.launch {
                val configuration: Configuration = DbManager.listenConfigurations().first()
                val handlers: WebViewActionHandlers = createWebViewActionHandlers(controller, layer, configuration)

                controller.setVisibility(false)
                controller.setJsBridge(bridge = { json ->
                    mindboxLogI("SDK <- receive message $json")
                    val message = gson.fromJson<BridgeMessage>(json).getOrNull()
                    if (!messageValidator.isValid(message)) {
                        return@setJsBridge
                    }

                    controller.executeOnViewThread {
                        when (message) {
                            is BridgeMessage.Request -> handleRequest(message, controller, handlers)
                            is BridgeMessage.Response -> handleResponse(message)
                            is BridgeMessage.Error -> handleError(message)
                            else -> mindboxLogW("Unknown message type: $message")
                        }
                    }
                })

                controller.setUserAgentSuffix(configuration.getShortUserAgent())

                layer.contentUrl?.let { contentUrl ->
                    runCatching {
                        gatewayManager.fetchWebViewContent(contentUrl)
                    }.onSuccess { response: String ->
                        currentWebViewOrigin = resolveOrigin(layer.baseUrl)
                        onContentPageLoaded(
                            content = WebViewHtmlContent(
                                baseUrl = layer.baseUrl ?: "",
                                html = response
                            )
                        )
                    }.onFailure { e ->
                        inAppFailureTracker.sendFailureWithContext(
                            inAppId = wrapper.inAppType.inAppId,
                            failureReason = FailureReason.WEBVIEW_LOAD_FAILED,
                            errorDescription = "Failed to fetch HTML content for In-App",
                            throwable = e
                        )
                        hide()
                        release()
                    }
                } ?: run {
                    inAppFailureTracker.sendFailureWithContext(
                        inAppId = wrapper.inAppType.inAppId,
                        failureReason = FailureReason.WEBVIEW_LOAD_FAILED,
                        errorDescription = "WebView content URL is null"
                    )
                }
            }
        }

        webViewController?.let { controller ->
            inAppFailureTracker.executeWithFailureTracking(
                inAppId = wrapper.inAppType.inAppId,
                failureReason = FailureReason.PRESENTATION_FAILED,
                errorDescription = "Error when trying WebView layout",
            ) {
                val view: WebViewPlatformView = controller.view
                if (view.parent !== inAppLayout) {
                    view.parent.safeAs<ViewGroup>()?.removeView(view)
                    inAppLayout.addView(view)
                }
            }
        } ?: run {
            inAppFailureTracker.sendFailureWithContext(
                inAppId = wrapper.inAppType.inAppId,
                failureReason = FailureReason.WEBVIEW_PRESENTATION_FAILED,
                errorDescription = "WebView controller is null when trying show inapp"
            )
            release()
        }
    }

    private fun onContentPageLoaded(content: WebViewHtmlContent) {
        webViewController?.let { controller ->
            controller.executeOnViewThread {
                controller.loadContent(content)
            }
            startTimer {
                inAppFailureTracker.sendFailureWithContext(
                    inAppId = wrapper.inAppType.inAppId,
                    failureReason = FailureReason.WEBVIEW_LOAD_FAILED,
                    errorDescription = "WebView initialization timed out after ${Stopwatch.stop(TIMER)}."
                )
                controller.executeOnViewThread {
                    hide()
                    release()
                }
            }
        } ?: run {
            mindboxLogW("WebView controller is null when loading content, skipping")
        }
    }

    private fun stopTimer() {
        closeInappTimer?.let { timer ->
            mindboxLogI("WebView initialization completed " + Stopwatch.stop(TIMER))
            timer.cancel()
        }
        closeInappTimer = null
    }

    private fun startTimer(onTimeOut: () -> Unit) {
        Stopwatch.start(TIMER)
        closeInappTimer = timer(
            initialDelay = INIT_TIMEOUT_MS,
            period = INIT_TIMEOUT_MS,
            action = { onTimeOut() }
        )
    }

    override fun show(currentRoot: MindboxView) {
        super.show(currentRoot)
        mindboxLogI("Try to show in-app with id ${wrapper.inAppType.inAppId}")
        wrapper.inAppType.layers.forEach { layer ->
            when (layer) {
                is Layer.WebViewLayer -> renderLayer(layer)
                else -> mindboxLogW("Layer is not supported")
            }
        }
        mindboxLogI("Show In-App ${wrapper.inAppType.inAppId} in holder ${this.hashCode()}")
        inAppLayout.requestFocus()
        webViewController?.let { controller ->
            currentRoot.registerBack(registerBackPressedCallback(controller))
        }
    }

    private fun registerBackPressedCallback(controller: WebViewController): OnBackPressedCallback {
        val isBackCallbackEnabled = backPressedCallback?.isEnabled ?: false
        clearBackPressedCallback()
        val callback = object : OnBackPressedCallback(isBackCallbackEnabled) {
            override fun handleOnBackPressed() {
                sendBackAction(controller)
            }
        }
        backPressedCallback = callback
        return callback
    }

    override fun reattach(currentRoot: MindboxView) {
        super.reattach(currentRoot)
        wrapper.inAppType.layers.forEach { layer ->
            when (layer) {
                is Layer.WebViewLayer -> renderLayer(layer)
                else -> mindboxLogW("Layer is not supported")
            }
        }
        inAppLayout.requestFocus()
        webViewController?.let { controller ->
            currentRoot.registerBack(registerBackPressedCallback(controller))
        }
    }

    override fun canReuseOnRestore(inAppId: String): Boolean = wrapper.inAppType.inAppId == inAppId

    override fun hide() {
        // Clean up timeout when hiding
        stopTimer()
        cancelPendingResponses("WebView In-App is hidden")
        clearBackPressedCallback()
        webViewController?.let { controller ->
            val view: WebViewPlatformView = controller.view
            inAppLayout.removeView(view)
        }
        super.hide()
    }

    override fun release() {
        super.release()
        // Clean up WebView resources
        stopTimer()
        cancelPendingResponses("WebView In-App is released")
        clearBackPressedCallback()
        currentWebViewOrigin = null
        webViewController?.destroy()
        webViewController = null
        backPressedCallback = null
    }

    private data class NavigationInterceptedPayload(
        val url: String
    )

    private data class ErrorPayload(
        val error: String
    )
}
