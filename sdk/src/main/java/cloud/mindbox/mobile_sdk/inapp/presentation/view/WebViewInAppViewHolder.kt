package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.*
import android.widget.RelativeLayout
import android.widget.Toast
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

    override val isActive: Boolean
        get() = isInAppMessageActive

    override fun bind() {
        currentDialog.setDismissListener {
            inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
            mindboxLogI("In-app dismissed by dialog click")
            hide()
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun addUrlSource(layer: Layer.WebViewLayer, inAppCallback: InAppCallback) {
        WebView.setWebContentsDebuggingEnabled(true)
        val webView = WebView(currentDialog.context).apply {
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                }

                override fun onLoadResource(view: WebView?, url: String?) {
                    super.onLoadResource(view, url)
                    if (url == "https://personalization-web-staging.mindbox.ru/web/contacts/28553/") {
                        mindboxLogI("onCompleted script. Close inapp")
                        hide()
                    }
                    mindboxLogI("onLoadResource. $url")
                }

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    mindboxLogI("shouldInterceptRequest request ${request?.url}}")
                    val response = super.shouldInterceptRequest(view, request)
                    return response
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    mindboxLogD("shouldOverrideUrlLoading: ${request?.url}")
                    return super.shouldOverrideUrlLoading(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    mindboxLogD("WEBVIEW onPageFinished: ${view?.contentHeight} $url")
                    if (url == "https://test-site-staging.mindbox.ru/mobile-sdk-test.html") {
                        val scripts = currentDialog.context.assets
                            .open("scripts.js")
                            .bufferedReader()
                            .use { it.readText() }
                        // Inject JavaScript to intercept fetch POST requests and capture the body
                        view?.evaluateJavascript(
                            scripts,
                            null
                        )
                    }
                }

                override fun shouldOverrideKeyEvent(view: WebView?, event: KeyEvent?): Boolean {
                    return super.shouldOverrideKeyEvent(view, event)
                }
            }
            addJavascriptInterface(
                WebAppInterface { request ->
                    this.post {
                        mindboxLogI("WEBVIEW Action ${request.action} ${request.screen}")
                        Toast.makeText(context, "${request.action} ${request.screen}", Toast.LENGTH_SHORT).show()
                        when (request.action) {
                            "collapse" -> hide()
                            "expand", "show" -> {
                                this.post {
                                    mindboxLogI("WEBVIEW Expand inapp ${this.contentHeight}")
                                }
                            }

                            "go-item" -> {
                                // hide()
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
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            setBackgroundColor(Color.parseColor("#00000000"))
        }

        currentDialog.addView(webView)
        Mindbox.mindboxScope.launch {
            val requestQueue: RequestQueue = Volley.newRequestQueue(currentDialog.context)
            val stringRequest = StringRequest(
                Request.Method.GET,
                layer.contentUrl,
                { response ->

                    val unEncodedHtml = currentDialog.context.assets
                        .open("webview.html")
                        .bufferedReader()
                        .use { it.readText() }

                    // val unEncodedHtml = response
//                        .replace("{ENDPOINT_ID}", "Test-staging.mobile-sdk-test-staging.mindbox.ru")
//                        .replace("{DEVICE_UUID}", MindboxPreferences.deviceUuid)

                    webView.loadDataWithBaseURL(layer.baseUrl, unEncodedHtml, "text/html", "UTF-8", null)
                },
                { error ->
                    hide()
                }
            )
            requestQueue.add(stringRequest)
        }
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
}
