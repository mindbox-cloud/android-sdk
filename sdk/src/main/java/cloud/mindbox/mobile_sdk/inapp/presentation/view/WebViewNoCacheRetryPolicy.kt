package cloud.mindbox.mobile_sdk.inapp.presentation.view

import cloud.mindbox.mobile_sdk.inapp.webview.isRecoverableScriptHttpError

/**
 * Decides whether an HTTP error on a page subresource warrants reloading the in-app's
 * content page with the HTTP cache bypassed.
 *
 * Exists for one failure mode: the CDN serves error responses with cacheable headers
 * (`Cache-Control: public, max-age=86400` observed live), so a transient 404 on a
 * bootstrap script gets stored by Chromium and replayed from the cache for a day —
 * the runtime never boots and every show dies on the init timeout. A single reload
 * with `LOAD_NO_CACHE` both recovers this show and overwrites the poisoned entry.
 *
 * Scope guards, in order:
 *  - only statuses >= 400 (redirects and successes are not errors);
 *  - only script resources (a broken image or stats beacon can't stop the runtime
 *    from booting — reloading a page over one would be a regression);
 *  - only before the runtime's `init` (after it the page has proven it can boot;
 *    a late lazy-module error must not tear down a live, visible in-app);
 *  - one retry per page load — if the fresh network answer is the same error, the
 *    existing init-timeout path closes the in-app as before;
 *  - the cache feature gate (the latched `MobileSdkShouldCacheInAppWebView` decision the
 *    WebView was created with — no separate retry toggle by design: with the cache off
 *    nothing can be poisoned and a reload would repeat the exact failed request), checked
 *    LAST and only for the retry decision, so [lastHttpErrorDetail] still enriches
 *    timeout telemetry when the cache is off.
 *
 * Pure logic, JVM-testable (mirrors [WebViewReadyChecker]). Writes are main-confined
 * (WebViewClient callbacks); the fields are @Volatile only because the init-timeout
 * telemetry reads them from the Timer thread.
 */
internal class WebViewNoCacheRetryPolicy(
    private val isCacheFeatureEnabled: () -> Boolean
) {

    /** "HTTP <status> for <url>" of the last script error — telemetry context for the init timeout. */
    @Volatile
    var lastHttpErrorDetail: String? = null
        private set

    /** True once a retry has been granted; later errors only update [lastHttpErrorDetail]. */
    @Volatile
    var hasRetried: Boolean = false
        private set

    /**
     * Records the error and returns true when the caller should perform the one-shot
     * no-cache reload of the content page.
     */
    fun onHttpError(url: String?, statusCode: Int?, hasInitialized: Boolean): Boolean {
        if (!isRecoverableScriptHttpError(url, statusCode)) return false
        lastHttpErrorDetail = "HTTP $statusCode for $url"
        if (hasInitialized) return false
        if (hasRetried) return false
        if (!isCacheFeatureEnabled()) return false
        hasRetried = true
        return true
    }
}
