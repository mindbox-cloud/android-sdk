package cloud.mindbox.mobile_sdk.embedded

import cloud.mindbox.mobile_sdk.fromJsonTyped
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.PlaceKey
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.models.convertToIso8601String
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.toJsonTyped
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

internal data class EmbeddedBlockPlaceRecord(
    @SerializedName("rememberedAt") val rememberedAt: String,
)

internal class EmbeddedBlockPlaceMemory(
    private val readRecordsJson: () -> String = { MindboxPreferences.embeddedBlockPlaceRecords },
    private val writeRecordsJson: (String) -> Unit = { json -> MindboxPreferences.embeddedBlockPlaceRecords = json },
    private val now: () -> Timestamp = { Timestamp(System.currentTimeMillis()) },
) {

    fun hasShownContent(place: PlaceKey): Boolean = place.value in records()

    fun rememberShownContent(place: PlaceKey) {
        val records = records()
        if (place.value in records) return

        val record = EmbeddedBlockPlaceRecord(rememberedAt = now().convertToIso8601String())
        if (!write(records + (place.value to record))) {
            mindboxLogE(
                "[EmbeddedBlock] Place '$place': could not write its record — the next launch " +
                    "starts it hidden again",
            )
            return
        }
        mindboxLogI(
            "[EmbeddedBlock] Place '$place' showed content — remembered, the next launch starts " +
                "it with a placeholder",
        )
    }

    fun forgetPlace(place: PlaceKey) {
        val records = records()
        if (place.value !in records) return

        if (!write(records - place.value)) return
        mindboxLogI(
            "[EmbeddedBlock] Place '$place' has nothing to show — forgotten, the next launch " +
                "starts it hidden",
        )
    }

    private fun records(): Map<String, EmbeddedBlockPlaceRecord> =
        loggingRunCatching(defaultValue = emptyMap()) {
            val json = readRecordsJson().ifEmpty { return@loggingRunCatching emptyMap() }
            cache?.takeIf { (cachedJson, _) -> cachedJson == json }?.second
                ?: (gson.fromJsonTyped<Map<String, EmbeddedBlockPlaceRecord>>(json) ?: emptyMap())
                    .also { parsed -> cache = json to parsed }
        }

    private fun write(records: Map<String, EmbeddedBlockPlaceRecord>): Boolean =
        loggingRunCatching(defaultValue = false) {
            val json = gson.toJsonTyped(records)
            writeRecordsJson(json)
            cache = json to records
            true
        }

    private companion object {
        private val gson by lazy { Gson() }
        private var cache: Pair<String, Map<String, EmbeddedBlockPlaceRecord>>? = null
    }
}
