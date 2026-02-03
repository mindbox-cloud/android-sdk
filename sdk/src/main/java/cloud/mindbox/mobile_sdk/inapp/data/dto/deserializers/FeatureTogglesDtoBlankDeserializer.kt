package cloud.mindbox.mobile_sdk.inapp.data.dto.deserializers

import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDtoBlank
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

private typealias FeatureTogglesDtoBlank = SettingsDtoBlank.FeatureTogglesDtoBlank

internal class FeatureTogglesDtoBlankDeserializer : JsonDeserializer<FeatureTogglesDtoBlank> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): FeatureTogglesDtoBlank {
        val jsonObject = json.asJsonObject

        return FeatureTogglesDtoBlank(
            shouldSendInAppShowError = jsonObject.getAsBooleanOrNull(SHOULD_SEND_INAPP_SHOW_ERROR)
        )
    }

    companion object {
        const val SHOULD_SEND_INAPP_SHOW_ERROR = "shouldSendInAppShowError"
    }
}
