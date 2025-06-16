package cloud.mindbox.mobile_sdk.inapp.data.dto.deserializers

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

internal class InAppIsPriorityDeserializer : JsonDeserializer<Boolean> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Boolean =
        json?.getBoolean() ?: false
}
