package cloud.mindbox.mobile_sdk.embedded.mock

import cloud.mindbox.mobile_sdk.BuildConfig
import cloud.mindbox.mobile_sdk.embedded.TempEmbeddedBlocksConfig
import org.json.JSONArray
import org.json.JSONObject

// MUST NOT REACH `develop`: injects a hardcoded `inlineBlocks` section into the fetched mobile
// config until the backend sends one. Delete the injection call when the contract lands.
internal object TempEmbeddedBlocksMockConfigSection {

    const val PLACE_MAIN = "main-screen-top"
    const val PLACE_SECONDARY = "main-screen-bottom"

    const val STORIES_STAGING_PAGE_URL =
        "https://mobile-static-staging.mindbox.ru/inapps/webview/content/stories.html"

    fun inject(rawConfig: String): String = runCatching {
        // A release build of the SDK must never inject the staging URL into a host app's config.
        if (!BuildConfig.DEBUG) return@runCatching rawConfig
        val root = JSONObject(rawConfig)
        if (root.has(TempEmbeddedBlocksConfig.SECTION_KEY)) return@runCatching rawConfig

        TempEmbeddedBlockUsage.report("mock inlineBlocks section injected into the mobile config")
        val placements = JSONArray()
            // The secondary place stays on the mock page, so the harness scenario switch
            // (SUCCESS/EMPTY/ERROR/SLOW) keeps a place to drive.
            .put(mockPlacement(PLACE_MAIN, STORIES_STAGING_PAGE_URL))
            .put(mockPlacement(PLACE_SECONDARY, pageUrl = null))
        root.put(TempEmbeddedBlocksConfig.SECTION_KEY, placements)
        // Android's JSONObject.toString() returns null instead of throwing — that null must never
        // erase the config.
        val serialized: String? = root.toString()
        serialized ?: rawConfig
    }.getOrDefault(rawConfig)

    private fun mockPlacement(placeSystemName: String, pageUrl: String?): JSONObject =
        JSONObject()
            .put(TempEmbeddedBlocksConfig.KEY_PLACE_SYSTEM_NAME, placeSystemName)
            .put(TempEmbeddedBlocksConfig.KEY_PAGE_URL, pageUrl ?: JSONObject.NULL)
}
