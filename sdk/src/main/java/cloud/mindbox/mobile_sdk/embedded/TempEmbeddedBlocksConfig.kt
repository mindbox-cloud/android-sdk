package cloud.mindbox.mobile_sdk.embedded

import cloud.mindbox.mobile_sdk.embedded.mock.TempEmbeddedBlockUsage
import org.json.JSONArray
import org.json.JSONObject

internal data class TempEmbeddedBlockPlacement(
    val placeSystemName: String,
    val pageUrl: String?,
)

internal data class TempEmbeddedBlocksConfig(
    val placements: List<TempEmbeddedBlockPlacement>,
) {

    fun placementsFor(placeSystemName: String): List<TempEmbeddedBlockPlacement> =
        placements.filter { it.placeSystemName == placeSystemName }

    companion object {

        internal const val SECTION_KEY = "inlineBlocks"
        internal const val KEY_PLACE_SYSTEM_NAME = "placeSystemName"
        internal const val KEY_PAGE_URL = "pageUrl"

        // Parsing walks the whole raw config on the main thread at every attach; the same string
        // instance is handed out by SharedPreferences until the config actually updates.
        @Volatile
        private var cache: Pair<String, TempEmbeddedBlocksConfig?>? = null

        fun parse(rawInAppConfig: String): TempEmbeddedBlocksConfig? {
            TempEmbeddedBlockUsage.report("temporary inlineBlocks config parser (MOBILE-344 replaces it)")
            cache?.let { (raw, parsed) -> if (raw === rawInAppConfig) return parsed }
            val parsed = parseUncached(rawInAppConfig)
            cache = rawInAppConfig to parsed
            return parsed
        }

        private fun parseUncached(rawInAppConfig: String): TempEmbeddedBlocksConfig? {
            val root = runCatching { JSONObject(rawInAppConfig) }.getOrNull() ?: return null
            val section = root.optJSONArray(SECTION_KEY) ?: return null
            return TempEmbeddedBlocksConfig(placements = section.parsePlacements())
        }

        private fun JSONArray.parsePlacements(): List<TempEmbeddedBlockPlacement> =
            (0 until length()).mapNotNull { index ->
                val entry = optJSONObject(index) ?: return@mapNotNull null
                val placeSystemName = entry.optString(KEY_PLACE_SYSTEM_NAME)
                    .takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val pageUrl = if (entry.isNull(KEY_PAGE_URL)) {
                    null
                } else {
                    entry.optString(KEY_PAGE_URL).takeIf { it.isNotBlank() }
                }
                TempEmbeddedBlockPlacement(
                    placeSystemName = placeSystemName,
                    pageUrl = pageUrl,
                )
            }
    }
}
