package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.*
import android.widget.RelativeLayout
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.R
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTypeWrapper
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback
import cloud.mindbox.mobile_sdk.inapp.presentation.MindboxView
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.removeChildById
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import kotlinx.coroutines.launch

internal class WebViewInAppViewHolder(
    override val wrapper: InAppTypeWrapper<InAppType.WebView>,
    private val inAppCallback: InAppCallback,
) : AbstractInAppViewHolder<InAppType.WebView>() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var webView: WebView? = null
    }

    override val isActive: Boolean
        get() = isInAppMessageActive

    override fun bind() {
        currentDialog.setDismissListener {
            inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
            mindboxLogI("In-app dismissed by dialog click")
            hide()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        mindboxLogI("WEBVIEW Create webview")
        return WebView(currentDialog.context).apply {
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    mindboxLogI("WebView: $error")
                    super.onReceivedError(view, request, error)
                }

                override fun onLoadResource(view: WebView?, url: String?) {
                    super.onLoadResource(view, url)
                    if (url == "https://personalization-web-staging.mindbox.ru/web/contacts/28553/") {
                        mindboxLogI("onCompleted script. Close inapp")
                        inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
                        hide()
                    }
                    mindboxLogI("onLoadResource. $url")
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    mindboxLogI("shouldInterceptRequest request ${request?.url}}")
                    val response = super.shouldInterceptRequest(view, request)
                    return response
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    mindboxLogD("shouldOverrideUrlLoading: ${request?.url}")
                    if (request?.url.toString().contains("www.21vek.by")) {
                        hide()
                        val intent = Intent(Intent.ACTION_VIEW, request?.url)
                        currentDialog.context.startActivity(intent)
                        return true
                    }
                    return super.shouldOverrideUrlLoading(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    mindboxLogD("WEBVIEW onPageFinished: ${view?.contentHeight} $url")
                }

                override fun shouldOverrideKeyEvent(view: WebView?, event: KeyEvent?): Boolean {
                    return super.shouldOverrideKeyEvent(view, event)
                }
            }
            addJavascriptInterface(
                WebAppInterface { action, data ->
                    this.post {
                        mindboxLogI("WEBVIEW Action $action $data")
                        when (action) {
                            "collapse", "close" -> {
                                if (data == "final") {
                                    inAppCallback.onInAppClick(
                                        wrapper.inAppType.inAppId,
                                        "",
                                        ""
                                    )
                                }
                                hide()
                            }

                            "expand", "show" -> {
                            }

                            "go-item" -> {
                            }
                        }
                    }
                },
                "SDK"
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
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            setBackgroundColor(Color.TRANSPARENT)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun addUrlSource(layer: Layer.WebViewLayer, inAppCallback: InAppCallback) {
        if (webView == null) {
            WebView.setWebContentsDebuggingEnabled(true)
            webView = createWebView()
            Mindbox.mindboxScope.launch {
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

                        webView?.loadDataWithBaseURL(
                            layer.baseUrl,
                            unEncodedHtml,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    },
                    { _ ->
                        hide()
                    }
                )
                requestQueue.add(stringRequest)
            }
        }
        currentDialog.addView(webView)
    }

    override fun show(currentRoot: MindboxView) {
        super.show(currentRoot)
        mindboxLogI("Try to show inapp with id ${wrapper.inAppType.inAppId}")
        wrapper.inAppType.layers.forEach { layer ->
            when (layer) {
                is Layer.WebViewLayer -> {
                    addUrlSource(layer, inAppCallback)
                }

                else -> {
                    mindboxLogD("Layer is not supported")
                }
            }
        }
        mindboxLogI("Show ${wrapper.inAppType.inAppId} on ${this.hashCode()}")
        currentDialog.requestFocus()
    }

    override fun initView(currentRoot: ViewGroup) {
        currentRoot.removeChildById(R.id.inapp_background_layout)
        super.initView(currentRoot)
    }

    override fun hide() {
        currentDialog.removeView(webView)
        super.hide()
    }
}
