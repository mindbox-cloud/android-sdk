package cloud.mindbox.mobile_sdk.embedded.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.Timestamp
import org.json.JSONObject
import java.lang.ref.WeakReference

internal class EmbeddedBlockWebViewPage(
    private val source: Source,
    context: Context,
    private val bridgeName: String,
    private val domReadyFlag: String? = null,
) : EmbeddedBlockPage {

    internal sealed class Source {
        data class Html(val html: String) : Source()

        data class Url(val url: String) : Source()
    }

    private val webView: WebView = WebView(context)

    override val view: View
        get() = webView

    override var onMessage: ((TempEmbeddedBlockPageMessage) -> Unit)? = null

    override var onMechanicMessage: ((payload: JSONObject) -> Unit)? = null

    override var onPageError: ((description: String) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isBridgeAdded = false

    // removeJavascriptInterface does not take effect for an already-loaded page, so silence after
    // pause() is enforced on the native side of the bridge as well.
    private var isConnected = false

    // The renderer died: the page may never be driven again, but the WebView object itself still
    // must be released.
    private var isRendererGone = false
    private var isReleased = false

    private val isDead: Boolean
        get() = isRendererGone || isReleased

    private var isPageResolved = false

    init {
        setUpWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setUpWebView() {
        webView.settings.javaScriptEnabled = true

        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false

        // Explicit: the mixed-content default depends on the HOST app's targetSdk.
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webView.settings.setGeolocationEnabled(false)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Only the main frame: dynamic iframes legitimately start at about:blank.
                if (!request.isForMainFrame) return false
                mindboxLogW("[EmbeddedBlock] Blocked navigation from the block page: ${request.url.toString().take(URL_LOG_LIMIT)}")
                return true
            }

            // API 21-23 call this one instead, and its default allows the navigation.
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                mindboxLogW("[EmbeddedBlock] Blocked navigation from the block page: ${url.take(URL_LOG_LIMIT)}")
                return true
            }

            @RequiresApi(Build.VERSION_CODES.M)
            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (!request.isForMainFrame) return
                reportPageError("Page load error ${error.errorCode}: ${error.description}")
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(
                view: WebView,
                errorCode: Int,
                description: String?,
                failingUrl: String?,
            ) {
                reportPageError("Page load error $errorCode: $description")
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                if (!request.isForMainFrame) return
                reportPageError("Page HTTP error ${errorResponse.statusCode}")
            }

            // Without this override a dead renderer CRASHES THE HOST APP (API 26+ default).
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                isRendererGone = true
                (view.parent as? ViewGroup)?.removeView(view)
                reportPageError("WebView renderer process is gone")
                return true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                startDomReadyPoll()
            }
        }

        webView.setBackgroundColor(Color.TRANSPARENT)

        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = View.OVER_SCROLL_NEVER
    }

    override fun load() {
        if (isDead) return
        addBridgeIfNeeded()
        isConnected = true
        isPageResolved = false
        webView.onResume()

        when (source) {
            is Source.Html -> webView.loadDataWithBaseURL(null, source.html, "text/html", "utf-8", null)
            is Source.Url -> {
                if (!isHttpsPageUrl(source.url)) {
                    mindboxLogE("[EmbeddedBlock] Refusing to load non-https block page: ${source.url.take(URL_LOG_LIMIT)}")
                    return
                }
                webView.loadUrl(source.url)
            }
        }
    }

    override fun pause() {
        isConnected = false
        if (isDead) return
        webView.onPause()
    }

    override fun resume() {
        if (isDead) return
        isConnected = true
        webView.onResume()
        if (!isPageResolved) startDomReadyPoll()
    }

    // Parsed, not prefix-matched: https://mobile-static.mindbox.ru@evil.example looks legitimate
    // and loads evil.example.
    private fun isHttpsPageUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return "https".equals(uri.scheme, ignoreCase = true) && uri.userInfo.isNullOrEmpty()
    }

    override fun release() {
        isConnected = false
        if (isReleased) return
        isReleased = true
        onMessage = null
        onMechanicMessage = null
        onPageError = null
        // A crashed WebView still must be destroyed, only its page must not be driven; and a
        // parented WebView must not be destroyed at all, hence the detach.
        if (!isRendererGone) webView.stopLoading()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    private fun reportPageError(description: String) {
        isPageResolved = true
        mindboxLogW("[EmbeddedBlock] $description")
        onPageError?.invoke(description)
    }

    private fun startDomReadyPoll() {
        val flag = domReadyFlag ?: return
        if (isPageResolved) return
        val probe =
            "(function(){" +
                "if(document.documentElement&&document.documentElement.dataset['$flag']==='true')" +
                "{return Math.max(document.body?document.body.scrollHeight:1,1);}" +
                "return 0;})()"
        pollDomReady(probe)
    }

    private fun pollDomReady(probe: String) {
        if (!isConnected || isDead || isPageResolved) return
        webView.evaluateJavascript(probe) { result -> onDomReadyProbeResult(probe, result) }
    }

    @VisibleForTesting
    internal fun onDomReadyProbeResult(probe: String, result: String?) {
        if (!isConnected || isDead || isPageResolved) return
        val height = result?.toDoubleOrNull() ?: 0.0
        if (height > 0) {
            isPageResolved = true
            onMessage?.invoke(TempEmbeddedBlockPageMessage.Ready(heightCssPx = height))
            return
        }
        mainHandler.postDelayed({ pollDomReady(probe) }, DOM_READY_POLL_INTERVAL.interval)
    }

    private fun addBridgeIfNeeded() {
        if (isBridgeAdded) return

        webView.addJavascriptInterface(EmbeddedBlockPageBridge(this, mainHandler), bridgeName)
        isBridgeAdded = true
    }

    // Known gap for the shared-bridge task: a one-shot `ready` posted into the pause window is
    // dropped here and never re-sent; the DOM protocol self-heals on resume, the bridge does not.
    private fun receive(body: String) {
        if (!isConnected) return

        val payload = runCatching { JSONObject(body) }.getOrNull()
        if (payload == null) {
            mindboxLogW("[EmbeddedBlock] Malformed block page message: ${body.logPreview()}")
            return
        }

        val message = TempEmbeddedBlockPageMessage.parse(payload)
        if (message == null) {
            val consumer = onMechanicMessage
            if (consumer == null) {
                mindboxLogI("[EmbeddedBlock] Page message with no consumer: ${body.logPreview()}")
            } else {
                consumer.invoke(payload)
            }
            return
        }
        if (message is TempEmbeddedBlockPageMessage.Ready) isPageResolved = true
        onMessage?.invoke(message)
    }

    // The body is the page's own text: untrusted, up to MAX_MESSAGE_LENGTH and free to carry
    // newlines. Logged raw, one message would break the log into dozens of lines and bury the tag.
    private fun String.logPreview(): String =
        take(BODY_LOG_LIMIT).replace('\n', ' ').replace('\r', ' ')

    private class EmbeddedBlockPageBridge(
        page: EmbeddedBlockWebViewPage,
        private val mainHandler: Handler,
    ) {

        // The WebView holds the bridge strongly, so the bridge points back weakly.
        private val pageRef = WeakReference(page)

        private var windowStart = Timestamp(0L)
        private var windowCount = 0

        @JavascriptInterface
        fun postMessage(json: String) {
            if (json.length > MAX_MESSAGE_LENGTH) return
            // A page looping postMessage would queue a main-looper runnable per call and ANR the
            // whole HOST app.
            if (!allowMessage()) return
            mainHandler.post { pageRef.get()?.receive(json) }
        }

        @Synchronized
        private fun allowMessage(): Boolean {
            val now = Timestamp(SystemClock.uptimeMillis())
            if ((now - windowStart).ms > RATE_WINDOW.interval) {
                windowStart = now
                windowCount = 0
            }
            windowCount++
            if (windowCount == MAX_MESSAGES_PER_WINDOW + 1) {
                mindboxLogW("[EmbeddedBlock] Block page floods the bridge, dropping messages")
            }
            return windowCount <= MAX_MESSAGES_PER_WINDOW
        }
    }

    private companion object {

        private const val MAX_MESSAGE_LENGTH = 16_384
        private const val BODY_LOG_LIMIT = 200
        private const val URL_LOG_LIMIT = 200
        private val DOM_READY_POLL_INTERVAL = Milliseconds(200L)
        private val RATE_WINDOW = Milliseconds(1_000L)
        private const val MAX_MESSAGES_PER_WINDOW = 30
    }
}
