package cloud.mindbox.mobile_sdk.models.operation.adapters

import cloud.mindbox.mobile_sdk.models.operation.CustomFields
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

class CustomerFieldsAdapter : TypeAdapter<CustomFields?>() {

    private val gson by lazy { Gson() }

    override fun write(out: JsonWriter?, value: CustomFields?) {
        LoggingExceptionHandler.runCatching {
            if (value == null) {
                out?.nullValue()
            } else {
                out?.jsonValue(gson.toJson(value.fields))
            }
        }
    }

    override fun read(`in`: JsonReader?): CustomFields? = `in`?.let { reader ->
        LoggingExceptionHandler.runCatching(defaultValue = null) {
            if (reader.peek() === JsonToken.NULL) {
                reader.nextNull()
                return@runCatching null
            }

            gson.fromJson<Map<String, Any?>?>(reader, Map::class.java)?.let(::CustomFields)
        }
    }

}
