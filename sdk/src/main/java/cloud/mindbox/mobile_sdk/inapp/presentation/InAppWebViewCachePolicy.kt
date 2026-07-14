package cloud.mindbox.mobile_sdk.inapp.presentation

import cloud.mindbox.mobile_sdk.inapp.data.managers.CACHE_INAPP_WEBVIEW_FEATURE
import cloud.mindbox.mobile_sdk.inapp.data.managers.FEATURE_TOGGLE_DEFAULT
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.MobileConfigSerializationManager
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponseBlank
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences

/**
 * The cache half of the WebView feature toggles (`MobileSdkShouldCacheInAppWebView`),
 * latched once per process from the cached config on disk — mirrors iOS's
 * `InAppWebViewDataStore.isCacheFeatureEnabled`.
 *
 * Read from the cache, not [cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggleManager]:
 * the prewarm's first WebView is created at SDK init, before any fresh config can populate
 * the manager — reading the live toggle there always sees the default (enabled), even when
 * the cached config already says otherwise. The decision is latched for the whole launch by
 * design: prewarm and every later show must agree, or the cache would be split between two
 * behaviors for the same session. This is also why the value can't just live in
 * [cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggleManager]'s toggle
 * map: that map is cleared and repopulated from the fresh config on every fetch, which would
 * silently un-latch this decision the first time a fresh config disagreed with the cache.
 */
internal class InAppWebViewCachePolicy(
    private val mobileConfigSerializationManager: MobileConfigSerializationManager
) {

    @Volatile
    private var latched: Boolean? = null

    val isCacheEnabled: Boolean
        @Synchronized get() = latched ?: extract(parseCachedConfigBlank()).also { latched = it }

    /**
     * Lets a caller that already parsed the cached config blank for its own purposes (the
     * prewarm manager, which needs the same blank for its layers and prewarm-toggle checks)
     * hand it over instead of this class deserializing the same JSON a second time.
     *
     * First value wins: a call after the toggle has already latched — from here or from
     * [isCacheEnabled] itself — is a no-op.
     */
    @Synchronized
    fun prime(configBlank: InAppConfigResponseBlank?) {
        if (latched == null) latched = extract(configBlank)
    }

    private fun extract(configBlank: InAppConfigResponseBlank?): Boolean =
        configBlank?.settings?.featureToggles?.toggles?.get(CACHE_INAPP_WEBVIEW_FEATURE) ?: FEATURE_TOGGLE_DEFAULT

    private fun parseCachedConfigBlank(): InAppConfigResponseBlank? {
        val configString = MindboxPreferences.inAppConfig
        if (configString.isBlank()) return null
        return mobileConfigSerializationManager.deserializeToConfigDtoBlank(configString)
    }
}
