package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.*
import android.widget.RelativeLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import cloud.mindbox.mobile_sdk.BuildConfig
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.fromJson
import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTypeWrapper
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback
import cloud.mindbox.mobile_sdk.inapp.presentation.MindboxView
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.getShortUserAgent
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.Constants
import cloud.mindbox.mobile_sdk.utils.Stopwatch
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.Timer
import java.util.TreeMap
import kotlin.concurrent.timer

internal class WebViewInAppViewHolder(
    override val wrapper: InAppTypeWrapper<InAppType.WebView>,
    private val inAppCallback: InAppCallback,
) : AbstractInAppViewHolder<InAppType.WebView>() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var webView: WeakReference<WebView> = WeakReference(null)
        private const val INIT_TIMEOUT_MS = 7_000L
        private const val TIMER = "CLOSE_INAPP_TIMER"
    }

    private var closeInappTimer: Timer? = null

    private val gson by mindboxInject { gson }

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
        webView.get()?.apply {
            val params = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER).apply {
                put("sdkVersion", Mindbox.getSdkVersion())
                put("endpointId", configuration.endpointId)
                put("deviceUuid", MindboxPreferences.deviceUuid)
                put("sdkVersionNumeric", Constants.SDK_VERSION_NUMERIC.toString())
                putAll(layer.params)
            }
            val provider = ParamProvider { key ->
                params[key]
            }
            addJavascriptInterface(
                WebAppInterface(provider) { action, data ->
                    handleWebViewAction(action, data, object : WebViewAction {

                        override fun onInit() {
                            // Cancel timeout when init is received
                            mindboxLogI("WebView initialization completed " + Stopwatch.stop(TIMER))
                            closeInappTimer?.cancel()
                            closeInappTimer = null

                            wrapper.inAppActionCallbacks.onInAppShown.onShown()
                            webView.get()?.isVisible = true
                        }

                        override fun onCompleted(data: String) {
                            runCatching {
                                val actionDto = gson.fromJson<BackgroundDto.LayerDto.ImageLayerDto.ActionDto>(data).getOrThrow()
                                val (url, payload) = when (actionDto) {
                                    is BackgroundDto.LayerDto.ImageLayerDto.ActionDto.RedirectUrlActionDto ->
                                        actionDto.value to actionDto.intentPayload
                                    is BackgroundDto.LayerDto.ImageLayerDto.ActionDto.PushPermissionActionDto ->
                                        "" to actionDto.intentPayload
                                }

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
                            onDestroy()
                        }

                        override fun onHide() {
                            webView.get()?.isInvisible = true
                        }

                        override fun onLog(message: String) {
                            webView.get()?.mindboxLogI("JS: $message")
                        }
                    })
                },
                "SdkBridge"
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(layer: Layer.WebViewLayer): WebView {
        mindboxLogI("Creating WebView for In-App: ${wrapper.inAppType.inAppId} with layer ${layer.type}")
        return WebView(currentDialog.context).apply {
            webViewClient = InAppWebClient(
                onCriticalError = {
                    mindboxLogE("WebView critical error. Destroying In-App.")
                    onDestroy()
                }
            )

            layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.defaultTextEncodingName = "utf-8"
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.allowContentAccess = true
            setBackgroundColor(Color.TRANSPARENT)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun addUrlSource(layer: Layer.WebViewLayer) {
        if (webView.get() == null) {
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
            webView = WeakReference(createWebView(layer).also {
                it.visibility = ViewGroup.INVISIBLE
            })
            Mindbox.mindboxScope.launch {
                val configuration = DbManager.listenConfigurations().first()
                withContext(Dispatchers.Main) {
                    addJavascriptInterface(layer, configuration)
                }

                webView.get()?.post {
                    webView.get()?.settings?.userAgentString += " " + configuration.getShortUserAgent()
                }
                val requestQueue: RequestQueue = Volley.newRequestQueue(currentDialog.context)
                val stringRequest = StringRequest(
                    Request.Method.GET,
                    layer.contentUrl,
                    { response ->
                        webView.get()?.loadDataWithBaseURL(
                            layer.baseUrl,
                            response,
                            "text/html",
                            "UTF-8",
                            null
                        )

                        Stopwatch.start(TIMER)
                        // Start timeout after loading the page
                        closeInappTimer = timer(
                            initialDelay = INIT_TIMEOUT_MS,
                            period = INIT_TIMEOUT_MS,
                            action = {
                                webView.get()?.post {
                                    if (closeInappTimer != null) {
                                        mindboxLogE("WebView initialization timed out after ${Stopwatch.stop(TIMER)}.")
                                        onDestroy()
                                    }
                                }
                            }
                        )
                    },
                    { error ->
                        mindboxLogE("Failed to fetch HTML content for In-App: $error. Destroying.")
                        onDestroy()
                    }
                )

                requestQueue.add(stringRequest)
            }
        }
        webView.get()?.let { webView ->
            // Remove the old webview if it not hidden on previous activity
            if (webView.isAttachedToWindow) {
                (webView.parent as ViewGroup).removeView(webView)
            }
            inAppLayout.addView(webView)
        } ?: onDestroy()
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

    override fun hide() {
        // Clean up timeout when hiding
        closeInappTimer?.cancel()
        closeInappTimer = null
        webView.get()?.let {
            inAppLayout.removeView(it)
        }
        super.hide()
    }

    private fun onDestroy() {
        // Clean up WebView resources
        webView.get()?.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        webView.clear()
        MindboxDI.appModule.inAppMessageViewDisplayer.hideCurrentInApp()
    }

    private interface WebViewAction {
        fun onInit()

        fun onCompleted(data: String)

        fun onClose()

        fun onHide()

        fun onLog(message: String)
    }

    private fun WebView.handleWebViewAction(action: String, data: String, actions: WebViewAction) {
        this.post {
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

    internal class InAppWebClient(private val onCriticalError: () -> Unit) : WebViewClient() {
        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val message = "WebView error: code=${error?.errorCode}, description=${error?.description}, url=${request?.url}"
                mindboxLogE(message)
                if (request?.isForMainFrame == true) {
                    onCriticalError()
                }
            }
        }

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in Java")
        override fun onReceivedError(
            view: WebView?,
            errorCode: Int,
            description: String?,
            failingUrl: String?
        ) {
            super.onReceivedError(view, errorCode, description, failingUrl)
            val message = "WebView error (legacy): code=$errorCode, description=$description, url=$failingUrl"
            mindboxLogE(message)
            // In the old API, we can't be sure if it's the main frame,
            // but any error is likely critical. The timeout will still act as a fallback.
            if (failingUrl == view?.originalUrl) {
                onCriticalError()
            }
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            mindboxLogD("shouldOverrideUrlLoading: ${request?.url}")
            return super.shouldOverrideUrlLoading(view, request)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            mindboxLogD("onPageFinished: $url")
            super.onPageFinished(view, url)
        }

        override fun shouldOverrideKeyEvent(view: WebView?, event: KeyEvent?): Boolean {
            mindboxLogD("shouldOverrideKeyEvent: $event")
            return super.shouldOverrideKeyEvent(view, event)
        }
    }
}
