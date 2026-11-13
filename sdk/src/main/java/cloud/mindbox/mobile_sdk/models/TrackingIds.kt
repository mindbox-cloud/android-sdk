package cloud.mindbox.mobile_sdk.models

import cloud.mindbox.mobile_sdk.maskAfter
import cloud.mindbox.mobile_sdk.models.TrackingId.Companion.VISIBLE_PREFIX_LENGTH
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName

internal data class TrackingId(
    @SerializedName(FIELD_TYPE) val type: String,
    @SerializedName(FIELD_VALUE) val value: String,
) {
    internal companion object {
        const val FIELD_IDS = "ids"
        const val FIELD_TYPE = "type"
        const val FIELD_VALUE = "value"
        const val VISIBLE_PREFIX_LENGTH = 8
    }
}

internal fun String.withoutTrackingIds(): String = runCatching {
    JsonParser.parseString(this).asJsonObject.apply { remove(TrackingId.FIELD_IDS) }.toString()
}.getOrDefault(this)

internal fun String.withMaskedTrackingIds(): String = runCatching {
    val json = JsonParser.parseString(this).asJsonObject
    val ids = json.getAsJsonArray(TrackingId.FIELD_IDS) ?: return this
    ids.forEach { id ->
        val entry = id.asJsonObject
        entry.get(TrackingId.FIELD_VALUE)?.asString?.let { value ->
            entry.addProperty(TrackingId.FIELD_VALUE, value.maskAfter(VISIBLE_PREFIX_LENGTH))
        }
    }
    json.toString()
}.getOrDefault(this)
