package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.Toast
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
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback
import cloud.mindbox.mobile_sdk.inapp.presentation.MindboxView
import cloud.mindbox.mobile_sdk.inapp.webview.*
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.getShortUserAgent
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.safeAs
import cloud.mindbox.mobile_sdk.utils.Constants
import cloud.mindbox.mobile_sdk.utils.MindboxUtils.Stopwatch
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
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
    private val pendingResponsesById: MutableMap<String, CompletableDeferred<BridgeMessage.Response>> =
        ConcurrentHashMap()

    private val gson: Gson by mindboxInject { this.gson }
    private val messageValidator: BridgeMessageValidator by lazy { BridgeMessageValidator() }

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
        layer: Layer.WebViewLayer
    ): WebViewActionHandlers {
        return WebViewActionHandlers().apply {
            registerSuspend(WebViewAction.READY) {
                executeReadyAction(layer)
            }
            register(WebViewAction.INIT) {
                executeInitAction(controller)
            }
            register(WebViewAction.CLICK) {
                executeCompletedAction(it)
            }
            register(WebViewAction.CLOSE) {
                executeCloseAction()
            }
            register(WebViewAction.HIDE) {
                executeHideAction(controller)
            }
            register(WebViewAction.LOG) {
                executeLogAction(it)
            }
            register(WebViewAction.TOAST) {
                executeToastAction(it)
            }
            register(WebViewAction.ALERT) {
                executeAlertAction(it)
            }
        }
    }

    private suspend fun executeReadyAction(layer: Layer.WebViewLayer): String {
        val configuration: Configuration = DbManager.listenConfigurations().first()

        val params: TreeMap<String, String> = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER).apply {
            put("sdkVersion", Mindbox.getSdkVersion())
            put("endpointId", configuration.endpointId)
            put("deviceUuid", MindboxPreferences.deviceUuid)
            put("sdkVersionNumeric", Constants.SDK_VERSION_NUMERIC.toString())
            putAll(layer.params)
        }

        return gson.toJson(params)
    }

    private fun executeInitAction(controller: WebViewController): String {
        mindboxLogI("WebView initialization completed " + Stopwatch.stop(TIMER))
        closeInappTimer?.cancel()
        closeInappTimer = null
        wrapper.inAppActionCallbacks.onInAppShown.onShown()
        controller.setVisibility(true)
        return BridgeMessage.EMPTY_PAYLOAD
    }

    private fun executeCompletedAction(message: BridgeMessage.Request): String {
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

    private fun executeCloseAction(): String {
        inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
        mindboxLogI("In-app dismissed by webview action")
        hide()
        release()
        return BridgeMessage.EMPTY_PAYLOAD
    }

    private fun executeHideAction(controller: WebViewController): String {
        controller.setVisibility(false)
        return BridgeMessage.EMPTY_PAYLOAD
    }

    private fun executeLogAction(message: BridgeMessage.Request): String {
        mindboxLogI("JS: ${message.payload}")
        return BridgeMessage.EMPTY_PAYLOAD
    }

    private fun executeToastAction(message: BridgeMessage.Request): String {
        webViewController?.view?.context?.let { context ->
            Toast.makeText(context, message.payload, Toast.LENGTH_LONG).show()
        }
        return BridgeMessage.EMPTY_PAYLOAD
    }

    private fun executeAlertAction(message: BridgeMessage.Request): String {
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
            }

            override fun onError(error: WebViewError) {
                val message = "WebView error: code=${error.code}, description=${error.description}, url=${error.url}"
                mindboxLogE(message)
                if (error.isForMainFrame == true) {
                    mindboxLogE("WebView critical error. Destroying In-App.")
                    release()
                }
            }
        })
        return controller
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
        val messageId: String = message.id
        if (messageId.isBlank()) {
            mindboxLogW("WebView request without id for action ${message.action}")
            return
        }
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
        sendActionInternal(controller, errorMessage)
    }

    private fun handleResponse(message: BridgeMessage.Response) {
        val messageId: String = message.id
        if (messageId.isBlank()) {
            mindboxLogW("WebView response without id for action ${message.action}")
            return
        }
        val responseDeferred: CompletableDeferred<BridgeMessage.Response>? = pendingResponsesById.remove(messageId)
        if (responseDeferred == null) {
            mindboxLogW("No pending response for id $messageId")
            return
        }
        if (!responseDeferred.isCompleted) {
            responseDeferred.complete(message)
        }
    }

    private fun handleError(message: BridgeMessage.Error) {
        mindboxLogW("WebView error: ${message.payload}")
        val messageId: String = message.id
        if (messageId.isBlank()) {
            mindboxLogW("WebView error without id for action ${message.action}")
            return
        }
        val responseDeferred: CompletableDeferred<BridgeMessage.Response>? = pendingResponsesById.remove(messageId)
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

    private fun addUrlSource(layer: Layer.WebViewLayer) {
        if (webViewController == null) {
            val controller: WebViewController = createWebViewController(layer)
            webViewController = controller
            val handlers: WebViewActionHandlers = createWebViewActionHandlers(controller, layer)

            controller.setVisibility(false)
            controller.setJsBridge(bridge = { json ->
                val message = gson.fromJson<BridgeMessage>(json).getOrNull()
                if (!messageValidator.isValid(message)) {
                    return@setJsBridge
                }

                when (message) {
                    is BridgeMessage.Request -> handleRequest(message, controller, handlers)
                    is BridgeMessage.Response -> handleResponse(message)
                    is BridgeMessage.Error -> handleError(message)
                    else -> mindboxLogW("Unknown message type: $message")
                }
            })

            Mindbox.mindboxScope.launch {
                val configuration: Configuration = DbManager.listenConfigurations().first()
                withContext(Dispatchers.Main) {
                    controller.setUserAgentSuffix(configuration.getShortUserAgent())
                }
                controller.setEventListener(object : WebViewEventListener {
                    override fun onPageFinished(url: String?) {
                        webViewController?.evaluateJavaScript(JS_CHECK_BRIDGE, ::checkEvaluateJavaScript)
                    }

                    override fun onError(error: WebViewError) {
                        super.onError(error)
                        mindboxLogE("WebView error: $error")
                        hide()
                    }
                })

                val requestQueue: RequestQueue = Volley.newRequestQueue(currentDialog.context)
                val stringRequest = StringRequest(
                    Request.Method.GET,
                    layer.contentUrl,
                    { response: String ->
                        onContentLoaded(
                            controller = controller,
                            content = WebViewHtmlContent(
                                baseUrl = layer.baseUrl ?: "",
                                html = response
                            )
                        )
                    },
                    { error: VolleyError ->
                        mindboxLogE("Failed to fetch HTML content for In-App: $error. Destroying.")
                        release()
                    }
                )
                requestQueue.add(stringRequest)
            }
        }

        webViewController?.let { controller ->
            val view: WebViewPlatformView = controller.view
            if (view.parent !== inAppLayout) {
                view.parent.safeAs<ViewGroup>()?.removeView(view)
                inAppLayout.addView(view)
            }
        } ?: release()
    }

    private fun onContentLoaded(controller: WebViewController, content: WebViewHtmlContent) {
        controller.executeOnViewThread {
            controller.loadContent(content)
            startTimer(controller)
        }
    }

    private fun startTimer(controller: WebViewController) {
        Stopwatch.start(TIMER)
        closeInappTimer = timer(
            initialDelay = INIT_TIMEOUT_MS,
            period = INIT_TIMEOUT_MS,
            action = {
                controller.executeOnViewThread {
                    if (closeInappTimer != null) {
                        mindboxLogE("WebView initialization timed out after ${Stopwatch.stop(TIMER)}.")
                        release()
                    }
                }
            }
        )
    }

    override fun show(currentRoot: MindboxView) {
        super.show(currentRoot)
        mindboxLogI("Try to show in-app with id ${wrapper.inAppType.inAppId}")
        wrapper.inAppType.layers.forEach { layer ->
            when (layer) {
                is Layer.WebViewLayer -> {
                    addUrlSource(layer)
                }

                else -> {
                    mindboxLogD("Layer is not supported")
                }
            }
        }
        mindboxLogI("Show In-App ${wrapper.inAppType.inAppId} in holder ${this.hashCode()}")
        inAppLayout.requestFocus()
    }

    override fun reattach(currentRoot: MindboxView) {
        super.reattach(currentRoot)
        wrapper.inAppType.layers.forEach { layer ->
            when (layer) {
                is Layer.WebViewLayer -> addUrlSource(layer)
                else -> mindboxLogW("Layer is not supported")
            }
        }
        inAppLayout.requestFocus()
    }

    override fun canReuseOnRestore(inAppId: String): Boolean = wrapper.inAppType.inAppId == inAppId

    override fun hide() {
        // Clean up timeout when hiding
        closeInappTimer?.cancel()
        closeInappTimer = null
        cancelPendingResponses("WebView In-App is hidden")
        webViewController?.let { controller ->
            val view: WebViewPlatformView = controller.view
            inAppLayout.removeView(view)
        }
        super.hide()
    }

    override fun release() {
        super.release()
        // Clean up WebView resources
        closeInappTimer?.cancel()
        closeInappTimer = null
        cancelPendingResponses("WebView In-App is released")
        webViewController?.destroy()
        webViewController = null
    }
}
