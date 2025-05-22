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
    ): SettingsDtoBlank.InappSettingsDtoBlank {
        val jsonObject = json.asJsonObject

        return InappSettingsDtoBlank(
            maxInappsPerSession = jsonObject.get(MAX_INAPPS_PER_SESSION)?.let { element ->
                when {
                    element.isJsonPrimitive && element.asJsonPrimitive.isNumber -> element.asInt
                    element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString.toIntOrNull()
                    else -> null
                }
            },
            maxInappsPerDay = jsonObject.get(MAX_INAPPS_PER_DAY)?.let { element ->
                when {
                    element.isJsonPrimitive && element.asJsonPrimitive.isNumber -> element.asInt
                    element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString.toIntOrNull()
                    else -> null
                }
            },
            minIntervalBetweenShows = jsonObject.get(MIN_INTERVAL_BETWEEN_SHOWS)?.let { element ->
                when {
                    element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString
                    else -> null
                }
            }
        )
    }

    companion object {
        const val MAX_INAPPS_PER_SESSION = "maxInappsPerSession"
        const val MAX_INAPPS_PER_DAY = "maxInappsPerDay"
        const val MIN_INTERVAL_BETWEEN_SHOWS = "minIntervalBetweenShows"
    }
}
