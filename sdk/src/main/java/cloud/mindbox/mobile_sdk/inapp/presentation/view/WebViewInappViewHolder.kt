package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.view.ViewGroup
import android.widget.RelativeLayout
import cloud.mindbox.mobile_sdk.BuildConfig
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.fromJson
import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Timer
import java.util.TreeMap
import kotlin.concurrent.timer

@OptIn(InternalMindboxApi::class)
internal class WebViewInAppViewHolder(
    override val wrapper: InAppTypeWrapper<InAppType.WebView>,
    private val inAppCallback: InAppCallback,
) : AbstractInAppViewHolder<InAppType.WebView>() {

    companion object {
        private const val INIT_TIMEOUT_MS = 7_000L
        private const val TIMER = "CLOSE_INAPP_TIMER"
    }

    private var closeInappTimer: Timer? = null
    private var webViewController: WebViewController? = null

    private val gson: Gson by mindboxInject { this.gson }

    override val isActive: Boolean
        get() = isInAppMessageActive

    override fun bind() {
        inAppLayout.setDismissListener {
            inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
            mindboxLogI("In-app dismissed by dialog click")
            hide()
        }
    }

    private fun addJavascriptInterface(layer: Layer.WebViewLayer, configuration: Configuration) {
        val controller: WebViewController = webViewController ?: return
        val params: TreeMap<String, String> = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER).apply {
            put("sdkVersion", Mindbox.getSdkVersion())
            put("endpointId", configuration.endpointId)
            put("deviceUuid", MindboxPreferences.deviceUuid)
            put("sdkVersionNumeric", Constants.SDK_VERSION_NUMERIC.toString())
            putAll(layer.params)
        }
        val bridge: WebViewJsBridge = object : WebViewJsBridge {
            override fun getParam(key: String): String? {
                return params[key]
            }

            override fun onAction(action: String, data: String) {
                handleWebViewAction(action, data, object : WebViewAction {
                    override fun onInit() {
                        mindboxLogI("WebView initialization completed " + Stopwatch.stop(TIMER))
                        closeInappTimer?.cancel()
                        closeInappTimer = null
                        wrapper.inAppActionCallbacks.onInAppShown.onShown()
                        controller.setVisibility(true)
                    }

                    override fun onCompleted(data: String) {
                        runCatching {
                            val actionDto: BackgroundDto.LayerDto.ImageLayerDto.ActionDto =
                                gson.fromJson<BackgroundDto.LayerDto.ImageLayerDto.ActionDto>(data).getOrThrow()
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
                        mindboxLogI("In-app completed by webview action with data: $data")
                    }

                    override fun onClose() {
                        inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
                        mindboxLogI("In-app dismissed by webview action")
                        hide()
                        release()
                    }

                    override fun onHide() {
                        controller.setVisibility(false)
                    }

                    override fun onLog(message: String) {
                        mindboxLogI("JS: $message")
                    }
                })
            }
        }
        controller.setJsBridge(bridge)
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

    fun addUrlSource(layer: Layer.WebViewLayer) {
        if (webViewController == null) {
            val controller: WebViewController = createWebViewController(layer)
            controller.setVisibility(false)
            webViewController = controller
            Mindbox.mindboxScope.launch {
                val configuration: Configuration = DbManager.listenConfigurations().first()
                withContext(Dispatchers.Main) {
                    addJavascriptInterface(layer, configuration)
                    controller.setUserAgentSuffix(configuration.getShortUserAgent())
                }
                val requestQueue: RequestQueue = Volley.newRequestQueue(currentDialog.context)
                val stringRequest = StringRequest(
                    Request.Method.GET,
                    layer.contentUrl,
                    { response: String ->
                        val content = WebViewHtmlContent(
                            baseUrl = layer.baseUrl ?: "",
                            html = response
                        )
                        controller.executeOnViewThread {
                            controller.loadContent(content)
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

    override fun show(currentRoot: MindboxView) {
        super.show(currentRoot)
        mindboxLogI("Try to show inapp with id ${wrapper.inAppType.inAppId}")
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
        webViewController?.destroy()
        webViewController = null
    }

    private interface WebViewAction {
        fun onInit()

        fun onCompleted(data: String)

        fun onClose()

        fun onHide()

        fun onLog(message: String)
    }

    private fun handleWebViewAction(action: String, data: String, actions: WebViewAction) {
        webViewController?.let { controller ->
            controller.executeOnViewThread {
                mindboxLogI("handleWebViewAction: Action $action with $data")
                when (action) {
                    "collapse", "close" -> actions.onClose()
                    "init" -> actions.onInit()
                    "hide" -> actions.onHide()
                    "click" -> actions.onCompleted(data)
                    "log" -> actions.onLog(data)
                }
            }
        }
    }
}
