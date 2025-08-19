package cloud.mindbox.mobile_sdk.inapp.data.dto.deserializers

import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDtoBlank
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

private typealias SlidingExpirationDtoBlank = SettingsDtoBlank.SlidingExpirationDtoBlank

internal class SlidingExpirationDtoBlankDeserializer : JsonDeserializer<SlidingExpirationDtoBlank> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): SlidingExpirationDtoBlank {
        val jsonObject = json.asJsonObject

        return SettingsDtoBlank.SlidingExpirationDtoBlank(
            config = jsonObject.getAsTimeSpan(CONFIG),
            pushTokenKeepalive = jsonObject.getAsTimeSpan(PUSH_TOKEN_KEEP_ALIVE),
        )
    }

    companion object {
        const val CONFIG = "config"
        const val PUSH_TOKEN_KEEP_ALIVE = "pushTokenKeepalive"
    }
}
