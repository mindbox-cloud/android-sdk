package cloud.mindbox.mobile_sdk.models.operation.adapters

import cloud.mindbox.mobile_sdk.models.operation.DateTime
import cloud.mindbox.mobile_sdk_core.returnOnException
import com.google.gson.TypeAdapter
import com.google.gson.internal.bind.util.ISO8601Utils
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.*

class DateTimeAdapter : TypeAdapter<DateTime>() {

    companion object {

        private const val WRITE_DATA_FORMAT = "dd.MM.yyyy HH:mm:ss.FFF"

    }

    override fun write(out: JsonWriter?, value: DateTime?) {
        runCatching {
            if (value == null) {
                out?.nullValue()
            } else {
                val formatter = SimpleDateFormat(WRITE_DATA_FORMAT, Locale.getDefault())
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

            reader.nextString()?.let { dateString ->
                ISO8601Utils.parse(dateString, ParsePosition(0))?.time?.let(::DateTime)
            }
        }.returnOnException { null }
    }

}
