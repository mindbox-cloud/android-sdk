package cloud.mindbox.mobile_sdk.models.operation.adapters

import cloud.mindbox.mobile_sdk.models.operation.Ids
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

internal class IdsAdapter : TypeAdapter<Ids?>() {

    private val gson by lazy { Gson() }

    override fun write(out: JsonWriter?, value: Ids?) {
        LoggingExceptionHandler.runCatching {
            if (value == null) {
                out?.nullValue()
            } else {
                out?.jsonValue(gson.toJson(value.ids))
            }
        }
    }

    override fun read(`in`: JsonReader?): Ids? = `in`?.let { reader ->
        LoggingExceptionHandler.runCatching(defaultValue = null) {
            if (reader.peek() === JsonToken.NULL) {
                reader.nextNull()
                return@runCatching null
            }

            // Workaround for case when id value is handled by gson as Double, not as String or Int
            // We manually parse value as String
            val ids = mutableMapOf<String, String?>()
            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                reader.beginObject()
                while (reader.peek() != JsonToken.END_OBJECT) {
                    LoggingExceptionHandler.runCatching {
                        val key = reader.nextName()
                        val valueString = reader.nextString()
                        ids[key] = valueString
                    }
                }
                reader.endObject()
            }

            Ids(ids)
        }
    }
}
