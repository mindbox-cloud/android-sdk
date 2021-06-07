package cloud.mindbox.mobile_sdk.models.operation.adapters

import cloud.mindbox.mobile_sdk.models.operation.DateTime
import cloud.mindbox.mobile_sdk.returnOnException
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.text.SimpleDateFormat
import java.util.*

class DateTimeAdapter : TypeAdapter<DateTime>() {

    private val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm:ss.FFF", Locale.getDefault())

    override fun write(out: JsonWriter?, value: DateTime?) {
        runCatching {
            if (value == null) {
                out?.nullValue()
            } else {
                out?.value(formatter.format(value))
            }
        }.returnOnException { out }
    }

    override fun read(`in`: JsonReader?): DateTime? = `in`?.let { reader ->
        runCatching {
            if (reader.peek() === JsonToken.NULL) {
                reader.nextNull()
                return@let null
            }

            reader.nextString()?.let { formatter.parse(it)?.time?.let(::DateTime) }
        }.returnOnException { null }
    }

}
