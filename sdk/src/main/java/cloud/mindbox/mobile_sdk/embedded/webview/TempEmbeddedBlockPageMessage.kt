package cloud.mindbox.mobile_sdk.embedded.webview

import cloud.mindbox.mobile_sdk.embedded.mock.TempEmbeddedBlockUsage
import org.json.JSONObject

internal sealed class TempEmbeddedBlockPageMessage {

    data class Ready(val heightCssPx: Double) : TempEmbeddedBlockPageMessage()

    data class HeightChanged(val heightCssPx: Double) : TempEmbeddedBlockPageMessage()

    /**
     * The page has nothing to put in the block — its targeting matched nothing, the mechanic is
     * switched off. Said explicitly, so that it is never confused with a page that rendered
     * nothing because it is broken.
     */
    data object Empty : TempEmbeddedBlockPageMessage()

    companion object {

        // On the class initializer, not on parse(): the DOM-flag protocol builds Ready directly and
        // never parses anything, so a marker inside parse() would leave that whole path silent.
        init {
            TempEmbeddedBlockUsage.report("temporary page message protocol (the shared JS bridge replaces it)")
        }

        private const val KEY_TYPE = "type"
        private const val KEY_HEIGHT = "height"

        fun parse(body: String): TempEmbeddedBlockPageMessage? {
            val payload = runCatching { JSONObject(body) }.getOrNull() ?: return null
            return parse(payload)
        }

        // Null means "not part of the common protocol": a mechanic-dialect message, or a
        // malformed one — the page may evolve ahead of the SDK.
        fun parse(payload: JSONObject): TempEmbeddedBlockPageMessage? =
            when (payload.optString(KEY_TYPE)) {
                "ready" -> height(payload)?.let { Ready(it) }
                "heightChanged" -> height(payload)?.let { HeightChanged(it) }
                "empty" -> Empty
                else -> null
            }

        private fun height(payload: JSONObject): Double? {
            if (!payload.has(KEY_HEIGHT)) return null
            val height = payload.optDouble(KEY_HEIGHT)
            return if (height.isFinite()) height else null
        }
    }
}
