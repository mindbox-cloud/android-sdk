package cloud.mindbox.mobile_sdk.inapp.data.dto.deserializers

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

internal class InAppDelayTimeDeserializer : JsonDeserializer<String?> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): String? {
        return json.getString()
    }

    companion object {
        const val INAPP_DELAY_TIME = "delayTime"
    }
}
