package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import cloud.mindbox.mobile_sdk.BuildConfig
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.fromJson
import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.inapp.data.validators.BridgeMessageValidator
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTypeWrapper
import cloud.mindbox.mobile_sdk.inapp.domain.extensions.sendFailureWithContext
import cloud.mindbox.mobile_sdk.inapp.domain.extensions.sendPresentationFailure
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
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.safeAs
import cloud.mindbox.mobile_sdk.utils.Constants
import cloud.mindbox.mobile_sdk.utils.MindboxUtils.Stopwatch
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TreeMap
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
        private const val JS_BRIDGE = "window.receiveFromSDK"
        private const val JS_CALL_BRIDGE = "$JS_BRIDGE(%s);"
        private const val JS_CHECK_BRIDGE = "typeof $JS_BRIDGE === 'function'"
    }

    private var closeInappTimer: Timer? = null
    private var webViewController: WebViewController? = null
    private var backPressedCallback: OnBackPressedCallback? = null
    private val pendingResponsesById: MutableMap<String, CompletableDeferred<BridgeMessage.Response>> =
        ConcurrentHashMap()

    private val gson: Gson by mindboxInject { this.gson }
    private val messageValidator: BridgeMessageValidator by lazy { BridgeMessageValidator() }
    private val gatewayManager: GatewayManager by mindboxInject { gatewayManager }
    private val inAppFailureTracker by mindboxInject { inAppFailureTracker }
    private var isWebViewVisible: Boolean = false

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
        val json = gson.toJson(message)
        controller.evaluateJavaScript(JS_CALL_BRIDGE.format(json)) { result ->
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
            register(WebViewAction.READY) {
                handleReadyAction(layer, configuration)
            }
            register(WebViewAction.INIT) {
                handleInitAction(controller)
            }
            register(WebViewAction.HIDE) {
                handleHideAction(controller)
            }
        }
    }

    private fun handleReadyAction(layer: Layer.WebViewLayer, configuration: Configuration): String {
        val params: TreeMap<String, String> = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER).apply {
            put("sdkVersion", Mindbox.getSdkVersion())
            put("endpointId", configuration.endpointId)
            put("deviceUuid", MindboxPreferences.deviceUuid)
            put("sdkVersionNumeric", Constants.SDK_VERSION_NUMERIC.toString())
            putAll(layer.params)
        }

        return gson.toJson(params)
    }

    private fun handleInitAction(controller: WebViewController): String {
        stopTimer()
        wrapper.inAppActionCallbacks.onInAppShown.onShown()
        controller.setVisibility(true)
        backPressedCallback?.isEnabled = true
        isWebViewVisible = true
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
            wrapper.inAppActionCallbacks.onInAppClick.onClick()
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
                webViewController?.evaluateJavaScript(JS_CHECK_BRIDGE, ::checkEvaluateJavaScript)
            }

            override fun onError(error: WebViewError) {
                mindboxLogE("WebView error: code=${error.code}, description=${error.description}, url=${error.url}")
                if (error.isForMainFrame == true) {
                    inAppFailureTracker.sendFailureWithContext(
                        inAppId = wrapper.inAppType.inAppId,
                        failureReason = FailureReason.WEBVIEW_INIT_FAILED,
                        errorDescription = "WebView error: code=${error.code}, description=${error.description}, url=${error.url}",
                        throwable = null
                    )
                    release()
                }
            }
        })
        return controller
    }

    private fun clearBackPressedCallback() {
        backPressedCallback?.remove()
        backPressedCallback = null
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
                mindboxLogE("evaluateJavaScript return unexpected response: $response")
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
        val errorMessage: BridgeMessage.Error = BridgeMessage.createErrorAction(message, error.message)
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
                    val message = gson.fromJson<BridgeMessage>(json).getOrNull()
                    if (!messageValidator.isValid(message)) {
                        if (!isWebViewVisible) {
                            inAppFailureTracker.sendFailureWithContext(
                                inAppId = wrapper.inAppType.inAppId,
                                failureReason = FailureReason.UNKNOWN_ERROR,
                                errorDescription = "Error on validation message from bridge before show WebView. message: $message"
                            )
                        }
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
                        onContentPageLoaded(
                            controller = controller,
                            content = WebViewHtmlContent(
                                baseUrl = layer.baseUrl ?: "",
                                html = response
                            )
                        )
                    }.onFailure { e ->
                        inAppFailureTracker.sendFailureWithContext(
                            inAppId = wrapper.inAppType.inAppId,
                            failureReason = FailureReason.HTML_LOAD_FAILED,
                            errorDescription = "Failed to fetch HTML content for In-App",
                            throwable = e
                        )
                        hide()
                        release()
                    }
                } ?: run {
                    inAppFailureTracker.sendFailureWithContext(
                        inAppId = wrapper.inAppType.inAppId,
                        failureReason = FailureReason.HTML_LOAD_FAILED,
                        errorDescription = "WebView content URL is null",
                        null
                    )
                    hide()
                }
            }
        }

        webViewController?.let { controller ->
            runCatching {
                val view: WebViewPlatformView = controller.view
                if (view.parent !== inAppLayout) {
                    view.parent.safeAs<ViewGroup>()?.removeView(view)
                    inAppLayout.addView(view)
                }
            }.onFailure { throwable ->
                inAppFailureTracker.sendPresentationFailure(
                    inAppId = wrapper.inAppType.inAppId,
                    errorDescription = "Error when trying WebView layout",
                    throwable = throwable
                )
            }
        } ?: run {
            inAppFailureTracker.sendPresentationFailure(
                inAppId = wrapper.inAppType.inAppId,
                errorDescription = "WebView controller is null when trying show inapp",
                null
            )
            release()
        }
    }

    private fun onContentPageLoaded(controller: WebViewController, content: WebViewHtmlContent) {
        controller.executeOnViewThread {
            controller.loadContent(content)
        }
        startTimer {
            controller.executeOnViewThread {
                inAppFailureTracker.sendFailureWithContext(
                    inAppId = wrapper.inAppType.inAppId,
                    failureReason = FailureReason.HTML_LOAD_FAILED,
                    errorDescription = "WebView initialization timed out after ${Stopwatch.stop(TIMER)}.",
                    throwable = null
                )
                hide()
                release()
            }
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
        isWebViewVisible = false
        super.hide()
    }

    override fun release() {
        super.release()
        // Clean up WebView resources
        stopTimer()
        cancelPendingResponses("WebView In-App is released")
        clearBackPressedCallback()
        webViewController?.destroy()
        webViewController = null
        isWebViewVisible = false
    }
}
