package cloud.mindbox.mobile_sdk.models

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
    }
}

internal fun Event.carriesTrackingIds(): Boolean {
    if (!eventType.isApplicationEvent) return false
    val ids = body?.let { raw ->
        runCatching {
            JsonParser.parseString(raw).asJsonObject.getAsJsonArray(TrackingId.FIELD_IDS)
        }.getOrNull()
    }
    return ids != null && !ids.isEmpty
}

internal fun Event.withoutTrackingIds(): Event = copy(
    body = body?.let { raw ->
        runCatching {
            JsonParser.parseString(raw).asJsonObject.apply { remove(TrackingId.FIELD_IDS) }.toString()
        }.getOrDefault(raw)
    },
)
