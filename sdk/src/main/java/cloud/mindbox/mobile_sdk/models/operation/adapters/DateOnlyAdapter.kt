package cloud.mindbox.mobile_sdk.models.operation.adapters

import cloud.mindbox.mobile_sdk.models.operation.DateOnly
import cloud.mindbox.mobile_sdk.returnOnException
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.text.SimpleDateFormat
import java.util.*

class DateOnlyAdapter : TypeAdapter<DateOnly>() {

    private val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun write(out: JsonWriter?, value: DateOnly?) {
        runCatching {
            if (value == null) {
                out?.nullValue()
            } else {
                out?.value(formatter.format(value))
            }
        }.returnOnException { out }
    }

    override fun read(`in`: JsonReader?): DateOnly? = `in`?.let { reader ->
        runCatching {
            if (reader.peek() === JsonToken.NULL) {
                reader.nextNull()
                return@let null
            }

            reader.nextString()?.let { formatter.parse(it)?.time?.let(::DateOnly) }
        }.returnOnException { null }
    }

}
