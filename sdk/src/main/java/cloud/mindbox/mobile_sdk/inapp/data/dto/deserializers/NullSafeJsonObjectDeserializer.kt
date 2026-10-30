package cloud.mindbox.mobile_sdk.inapp.data.dto.deserializers

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.lang.reflect.Type

internal class NullSafeJsonObjectDeserializer : JsonDeserializer<JsonObject?> {

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): JsonObject? = json?.takeIf { element -> element.isJsonObject }?.asJsonObject
}
