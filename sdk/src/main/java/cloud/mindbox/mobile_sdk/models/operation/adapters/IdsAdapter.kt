package cloud.mindbox.mobile_sdk.models.operation.adapters

import cloud.mindbox.mobile_sdk.models.operation.Ids
import cloud.mindbox.mobile_sdk.returnOnException
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

            gson.fromJson<Map<String, String?>?>(reader, Map::class.java)?.let(::Ids)
        }.returnOnException { null }
    }

}
