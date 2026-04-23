package cloud.mindbox.mobile_sdk.inapp.presentation.view.cache

import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer

/**
 * Prototype knobs and URL helpers for the in-app WebView offline cache.
 *
 * Mirrors the iOS prototype flags in MindboxWebViewFacade.swift:
 *  - [enabled] is the master switch; when false, [InAppWebViewAssetCache] and
 *    [InAppWebViewPrefetcher] become no-ops and the SDK falls back to the
 *    existing online-only flow.
 *  - URL helpers here reproduce the byendpoint/popmechanic/quizzes well-known
 *    hosts so prefetch and inline can materialize exactly the set of runtime
 *    dependencies the popup would otherwise fetch itself.
 */
internal object WebViewCacheConfig {

    /**
     * Master toggle for the disk cache + prefetch + inline pipeline.
     *
     * Default true to exercise the cache in prototype builds. Consumers that
     * want to kill-switch it at runtime (A/B test, emergency rollback) can
     * flip this before the first in-app is shown.
     */
    @Volatile
    var enabled: Boolean = true
}

/**
 * Well-known URL of the per-endpoint bootstrap script that tracker.js fetches at runtime to
 * install `window.PopMechanic`, `window.MindboxEndpointSettings`, etc. Pre-caching and
 * inlining this script is what makes the popup render without network at popup time.
 */
internal fun byendpointScriptUrl(endpointId: String): String? {
    val trimmed = endpointId.trim().lowercase()
    if (trimmed.isEmpty()) return null
    return "https://web-static.mindbox.ru/js/byendpoint/$trimmed.js"
}

/**
 * PopMechanic form config. byendpoint's `pt()` returns `window.__POPMECHANIC_INIT` if set,
 * avoiding the runtime fetch.
 */
internal fun popMechanicInitUrl(endpointId: String): String? {
    val trimmed = endpointId.trim().lowercase()
    if (trimmed.isEmpty()) return null
    return "https://web-static.mindbox.ru/personalization/byendpoint/$trimmed.json"
}

/**
 * Quizzes config. byendpoint skips `ft()` fetch when `window.__PRELOADED_QUIZZES_CONFIG` is set.
 */
internal fun quizzesConfigUrl(endpointId: String): String? {
    val trimmed = endpointId.trim().lowercase()
    if (trimmed.isEmpty()) return null
    return "https://web-static.mindbox.ru/quizzes/byendpoint/$trimmed.json"
}

/**
 * The quizzes ES-module library that byendpoint attaches dynamically via
 * `<script type="module" async src=".../quizzes.js">`. Offline that fetch fails; we cache
 * the script and inline it as a module so byendpoint's dynamic load has nothing left to do.
 */
internal const val QUIZZES_STABLE_SCRIPT_URL: String = "https://web-static.mindbox.ru/quizzes/stable/quizzes.js"

/**
 * Returns the JS global variable name that a given preloaded JSON URL should be assigned to,
 * so byendpoint skips its runtime fetch.
 */
internal fun preloadGlobalVariableName(urlString: String): String? = when {
    urlString.contains("/personalization/byendpoint/") -> "__POPMECHANIC_INIT"
    urlString.contains("/quizzes/byendpoint/") -> "__PRELOADED_QUIZZES_CONFIG"
    else -> null
}

/**
 * Walks the domain model tree and returns contentUrls of all WebView layers across all in-apps.
 * Mirrors `InAppWebViewAssetCache.extractWebViewContentUrls(from:)` on iOS.
 */
internal fun extractWebViewContentUrls(inapps: List<InApp>?): List<String> {
    if (inapps.isNullOrEmpty()) return emptyList()
    val out = mutableListOf<String>()
    for (inapp in inapps) {
        for (variant in inapp.form.variants) {
            val layers: List<Layer> = when (variant) {
                is InAppType.WebView -> variant.layers
                is InAppType.ModalWindow -> variant.layers
                is InAppType.Snackbar -> variant.layers
            }
            for (layer in layers) {
                if (layer is Layer.WebViewLayer) {
                    layer.contentUrl?.takeIf { it.isNotEmpty() }?.let(out::add)
                }
            }
        }
    }
    return out
}
