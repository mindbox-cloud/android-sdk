package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.*
import android.widget.RelativeLayout
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTypeWrapper
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback
import cloud.mindbox.mobile_sdk.inapp.presentation.MindboxView
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.Locale

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
    private fun createWebView(layer: Layer.WebViewLayer): WebView {
        mindboxLogI("WEBVIEW Create webview")
        return WebView(currentDialog.context).apply {
            webViewClient = InAppWebClient()
            addJavascriptInterface(
                WebAppInterface { action, data ->
                    handleWebViewAction(action, data, object : WebViewAction {

                        override fun onInit() {
                            webView?.visibility = ViewGroup.VISIBLE
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
                        }
                    })
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
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"
            setBackgroundColor(Color.TRANSPARENT)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun addUrlSource(layer: Layer.WebViewLayer) {
        if (webView == null) {
            WebView.setWebContentsDebuggingEnabled(true)
            webView = createWebView(layer).also {
                it.visibility = ViewGroup.INVISIBLE
            }
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

                        //  val unEncodedHtml = response

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

    override fun initView(currentRoot: ViewGroup) {
        // currentRoot.removeChildById(R.id.inapp_background_layout)
        super.initView(currentRoot)
    }

    override fun hide() {
        currentDialog.removeView(webView)
        super.hide()
    }

    private fun onDestroy() {
        // Clean up WebView resources
        webView?.apply {
            stopLoading()
            removeAllViews()
            destroy()
            webView = null
        }
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
                    if (data == "final") {
                        actions.onCompleted()
                    }
                    actions.onClose()
                }

                "init" -> {
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
            mindboxLogI("onReceivedError: $error")
            super.onReceivedError(view, request, error)
        }

        override fun onLoadResource(view: WebView?, url: String?) {
            mindboxLogI("onLoadResource: $url")
            super.onLoadResource(view, url)
        }

        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?
        ): WebResourceResponse? {

            if (request?.url?.toString()?.lowercase(Locale.getDefault())?.endsWith("/favicon.ico") == true) {
                val inputStream = "".byteInputStream(Charset.defaultCharset())
                return WebResourceResponse("text", "UTF-8", inputStream)
            }
            mindboxLogI("shouldInterceptRequest request ${request?.url}}")

            return super.shouldInterceptRequest(view, request)
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

    class QuizzesRequest(
        private val onSuccess: (JSONObject?) -> Unit
    ) : JsonObjectRequest(
        Method.POST,
        "https://quizzes-staging.mindbox.ru/api/v0/init",
        JSONObject(),
        object : Response.Listener<JSONObject> {
            override fun onResponse(response: JSONObject?) {
                mindboxLogD("Response: $response")
                onSuccess(response)
            }
        },
        object : Response.ErrorListener {
            override fun onErrorResponse(error: VolleyError?) {
                mindboxLogD("Error: $error")
                onSuccess(null)
            }
        }
    ) {
        override fun getHeaders(): MutableMap<String, String> {
            return mutableMapOf(
                "accept-language" to "en-US,en;q=0.9",
                "x-client-id" to "Test-staging.mobile-sdk-test-staging.mindbox.ru",
                "x-location" to "https://test-site-staging.mindbox.ru/mobile-sdk-test.html",
                "x-user-uid" to MindboxPreferences.deviceUuid,
            )
        }
    }
}
