package cloud.mindbox.mobile_sdk_core.models.operation.adapters

import cloud.mindbox.mobile_sdk_core.logOnException
import cloud.mindbox.mobile_sdk_core.models.operation.Ids
import cloud.mindbox.mobile_sdk_core.returnOnException
import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.util.*

class IdsAdapter : TypeAdapter<Ids?>() {

    private val gson by lazy { Gson() }

    override fun write(out: JsonWriter?, value: Ids?) {
        runCatching {
            if (value == null) {
                out?.nullValue()
            } else {
                out?.jsonValue(gson.toJson(value.ids))
            }
        }.returnOnException { out }
    }

    override fun read(`in`: JsonReader?): Ids? = `in`?.let { reader ->
        runCatching {
            if (reader.peek() === JsonToken.NULL) {
                reader.nextNull()
                return@let null
            }

            // Workaround for case when id value is handled by gson as Double, not as String or Int
            // We manually parse value as String
            val ids = mutableMapOf<String, String?>()
            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                reader.beginObject()
                while (reader.peek() != JsonToken.END_OBJECT) {
                    runCatching {
                        val key = reader.nextName()
                        val valueString = reader.nextString()
                        ids[key] = valueString
                    }.logOnException()
                }
                reader.endObject()
            }

            Ids(ids)
        }.returnOnException { null }
    }

}
