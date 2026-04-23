package cloud.mindbox.mobile_sdk.inapp.presentation.view.cache

import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppConfig
import cloud.mindbox.mobile_sdk.logger.mindboxLogD

/**
 * Walks an [InAppConfig] snapshot and kicks off asynchronous prefetch of everything the
 * cache needs to render the webview in-apps offline. Delegates the actual HTTP + disk IO
 * to [InAppWebViewAssetCache].
 *
 * Mirrors iOS `InAppConfigurationManager.prefetchWebViewAssets()` — extracted into its own
 * object to keep the caching concern out of the Android config repository.
 */
internal object InAppWebViewPrefetcher {

    /**
     * Called on every successful config update. Cheap if the config contains no webview
     * in-apps (returns without touching the network).
     *
     * [endpointId] is the SDK's configured endpoint, used to build the per-endpoint
     * byendpoint/popmechanic/quizzes bootstrap URLs. When null or empty those are skipped.
     */
    fun prefetch(config: InAppConfig?, endpointId: String?) {
        if (!WebViewCacheConfig.enabled) {
            mindboxLogD("[WebViewCache] Prefetcher skipped: feature flag disabled")
            return
        }
        val urls = extractWebViewContentUrls(config?.inApps)
        if (urls.isEmpty()) {
            mindboxLogD(
                "[WebViewCache] Prefetcher no-op: config has no webview in-apps " +
                    "(inApps=${config?.inApps?.size ?: 0})"
            )
            return
        }

        // Pre-download runtime dependencies so the cache-hit path can inline them:
        //  - personalization/quizzes JSONs → `window.__POPMECHANIC_INIT` / `__PRELOADED_QUIZZES_CONFIG`
        //    byendpoint checks these before its `pt()` / `ft()` runtime fetches.
        //  - byendpoint.js itself → installs `window.PopMechanic`.
        //  - quizzes.js ES module → byendpoint dynamically injects this at runtime.
        val extras = buildList {
            if (!endpointId.isNullOrBlank()) {
                popMechanicInitUrl(endpointId)?.let(::add)
                quizzesConfigUrl(endpointId)?.let(::add)
                byendpointScriptUrl(endpointId)?.let(::add)
            }
            add(QUIZZES_STABLE_SCRIPT_URL)
        }

        mindboxLogD(
            "[WebViewCache] Prefetching assets for ${urls.size} webview in-app(s), " +
                "endpointId=${endpointId.orEmpty().ifBlank { "<blank>" }}, extras=${extras.size}"
        )
        InAppWebViewAssetCache.prefetch(
            contentUrls = urls,
            extraScripts = extras
        )
    }
}
