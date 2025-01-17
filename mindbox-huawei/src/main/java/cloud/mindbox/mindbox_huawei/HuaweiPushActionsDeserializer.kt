package cloud.mindbox.mindbox_huawei

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.lang.reflect.Type

internal class HuaweiPushActionsDeserializer : JsonDeserializer<List<PushAction>> {

    override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext): List<PushAction> {
        return try {
            when {
                json.isJsonArray -> context.deserialize(json, typeOfT)
                json.isJsonPrimitive && json.asJsonPrimitive.isString -> {
                    val jsonString = json.asString
                    val element = JsonParser.parseString(jsonString)
                    if (element.isJsonArray) {
                        context.deserialize(element, typeOfT)
                    } else {
                        emptyList()
                    }
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
