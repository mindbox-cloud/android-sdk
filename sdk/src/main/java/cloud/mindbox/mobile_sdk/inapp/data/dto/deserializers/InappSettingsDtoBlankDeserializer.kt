package cloud.mindbox.mobile_sdk.inapp.data.dto.deserializers

import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDtoBlank
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

private typealias InappSettingsDtoBlank = SettingsDtoBlank.InappSettingsDtoBlank

internal class InappSettingsDtoBlankDeserializer : JsonDeserializer<InappSettingsDtoBlank> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): InappSettingsDtoBlank {
        val jsonObject = json.asJsonObject

        return InappSettingsDtoBlank(
            maxInappsPerSession = jsonObject.getAsIntOrNull(MAX_INAPPS_PER_SESSION),
            maxInappsPerDay = jsonObject.getAsIntOrNull(MAX_INAPPS_PER_DAY),
            minIntervalBetweenShows = jsonObject.getAsTimeSpan(MIN_INTERVAL_BETWEEN_SHOWS)
        )
    }

    companion object {
        const val MAX_INAPPS_PER_SESSION = "maxInappsPerSession"
        const val MAX_INAPPS_PER_DAY = "maxInappsPerDay"
        const val MIN_INTERVAL_BETWEEN_SHOWS = "minIntervalBetweenShows"
    }
}
