package cloud.mindbox.mobile_sdk.inapp.data.dto.deserializers

import com.google.gson.Gson
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

internal class WebViewParamsDeserializer : JsonDeserializer<Map<String, String>?> {

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Map<String, String>? {
        if (json.isJsonNull) return null
        if (!json.isJsonObject) return emptyMap()
        return json.asJsonObject.entrySet().mapNotNull { (key, value) ->
            value.toParamString()?.let { key to it }
        }.toMap()
    }

    private fun JsonElement.toParamString(): String? {
        if (isJsonNull) return null
        return when {
            isJsonPrimitive -> when {
                asJsonPrimitive.isString -> asString
                asJsonPrimitive.isNumber -> asNumber.toString()
                asJsonPrimitive.isBoolean -> asBoolean.toString()
                else -> asString
            }
            else -> GSON.toJson(this)
        }
    }

    private companion object {
        private val GSON = Gson()
    }
}
