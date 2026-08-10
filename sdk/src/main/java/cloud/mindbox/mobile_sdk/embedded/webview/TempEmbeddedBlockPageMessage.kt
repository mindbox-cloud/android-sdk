package cloud.mindbox.mobile_sdk.embedded.webview

import org.json.JSONObject

internal sealed class TempEmbeddedBlockPageMessage {

    data class Ready(val heightCssPx: Double) : TempEmbeddedBlockPageMessage()

    data class HeightChanged(val heightCssPx: Double) : TempEmbeddedBlockPageMessage()

    companion object {

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
                else -> null
            }

        private fun height(payload: JSONObject): Double? {
            if (!payload.has(KEY_HEIGHT)) return null
            val height = payload.optDouble(KEY_HEIGHT)
            return if (height.isFinite()) height else null
        }
    }
}
