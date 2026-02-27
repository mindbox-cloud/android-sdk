package cloud.mindbox.mobile_sdk.inapp.data.dto.deserializers

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

internal class InAppTagsDeserializer : JsonDeserializer<Map<String, String>?> {

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): Map<String, String>? {
        if (json.isJsonNull) return null
        if (!json.isJsonObject) return null
        return json.asJsonObject.entrySet().mapNotNull { (key, value) ->
            if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                key to value.asString
            } else {
                null
            }
        }.toMap()
    }

    companion object {
        const val TAGS = "tags"
    }
}
