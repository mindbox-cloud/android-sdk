package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.*
import android.widget.RelativeLayout
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.MindboxDI
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

    override val isActive: Boolean
        get() = isInAppMessageActive

    override fun bind() {
        currentDialog.setDismissListener {
            inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
            mindboxLogI("In-app dismissed by dialog click")
            hide()
        }
    }

    private fun addJavascriptInterface(layer: Layer.WebViewLayer, configuration: Configuration) {
        webView.get()?.apply {
            addJavascriptInterface(
                WebAppInterface(configuration = configuration) { action, data ->
                    handleWebViewAction(action, data, object : WebViewAction {

                        override fun onInit() {
                            wrapper.onInAppShown.onShown()
                            webView.get()?.visibility = ViewGroup.VISIBLE
                        }

                        override fun onCompleted() {
                            inAppCallback.onInAppClick(
                                wrapper.inAppType.inAppId,
                                layer.actionUrl ?: "",
                                ""
                            )
                            mindboxLogI("In-app dismissed by webview action")
                            hide()
                        }

                        override fun onClose() {
                            inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
                            mindboxLogI("In-app dismissed by webview action")
                            hide()
                            onDestroy()
                        }
                    })
                },
                "SDK"
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(layer: Layer.WebViewLayer): WebView {
        mindboxLogI("WEBVIEW Create webview")
        return WebView(currentDialog.context).apply {
            webViewClient = InAppWebClient()

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
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            setBackgroundColor(Color.TRANSPARENT)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun addUrlSource(layer: Layer.WebViewLayer) {
        if (webView.get() == null) {
            WebView.setWebContentsDebuggingEnabled(true)
            webView = WeakReference(createWebView(layer).also {
                it.visibility = ViewGroup.INVISIBLE
            })
            Mindbox.mindboxScope.launch {
                val configuration = DbManager.listenConfigurations().first()
                withContext(Dispatchers.Main) {
                    addJavascriptInterface(layer, configuration)
                }

                webView.get()?.post({
                    webView.get()?.settings?.userAgentString += " " + configuration.getShortUserAgent()
                })
                val requestQueue: RequestQueue = Volley.newRequestQueue(currentDialog.context)
                val stringRequest = StringRequest(
                    Request.Method.GET,
                    layer.contentUrl,
                    { response ->
//                        val unEncodedHtml = currentDialog.context.assets
//                            .open("webview.html")
//                            .bufferedReader()
//                            .use { it.readText() }

                        val unEncodedHtml = response

                        webView.get()?.loadDataWithBaseURL(
                            layer.baseUrl,
                            unEncodedHtml,
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
                                    mindboxLogI("WebView time out to init " + Stopwatch.stop(TIMER))
                                    onDestroy()
                                }
                            },
                        )
                    },
                    { _ ->
                        onDestroy()
                    }
                )

                requestQueue.add(stringRequest)
            }
        }
        webView.get()?.let { webView ->
            currentDialog.addView(webView)
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
        mindboxLogI("Show ${wrapper.inAppType.inAppId} on ${this.hashCode()}")
        currentDialog.requestFocus()
    }

    override fun hide() {
        // Clean up timeout when hiding
        closeInappTimer?.cancel()
        closeInappTimer = null
        webView.get()?.let {
            currentDialog.removeView(it)
        }
        super.hide()
    }

    private fun onDestroy() {
        // Clean up WebView resources
        webView.get()?.apply {
            stopLoading()
            removeAllViews()
            destroy()
            webView.clear()
        }
        MindboxDI.appModule.inAppMessageViewDisplayer.hideCurrentInApp()
    }

    private interface WebViewAction {
        fun onInit()

        fun onCompleted()

        fun onClose()
    }

    private fun WebView.handleWebViewAction(action: String, data: String, actions: WebViewAction) {
        this.post {
            mindboxLogI("handleWebViewAction: Action $action with $data")
            when (action) {
                "collapse", "close" -> {
                    actions.onClose()
                }

                "init" -> {
                    // Cancel timeout when init is received
                    mindboxLogI("WebView initialization completed " + Stopwatch.stop(TIMER))
                    closeInappTimer?.cancel()

                    if (data == "quiz") {
                        actions.onInit()
                    } else {
                        actions.onClose()
                    }
                }

                "expand", "show", "go-item" -> {
                }
            }
        }
    }

    internal class InAppWebClient : WebViewClient() {
        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mindboxLogE("onReceivedError: ${error?.description}")
            } else {
                mindboxLogE("onReceivedError: $error")
            }
            super.onReceivedError(view, request, error)
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
