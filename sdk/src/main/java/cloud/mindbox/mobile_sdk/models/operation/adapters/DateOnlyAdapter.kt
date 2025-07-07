package cloud.mindbox.mobile_sdk.models.operation.adapters

import cloud.mindbox.mobile_sdk.models.operation.DateOnly
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.text.SimpleDateFormat
import java.util.Locale

internal class DateOnlyAdapter : TypeAdapter<DateOnly>() {

    private val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun write(out: JsonWriter?, value: DateOnly?) {
        LoggingExceptionHandler.runCatching {
            if (value == null) {
                out?.nullValue()
            } else {
                out?.value(formatter.format(value))
            }
        }
    }

    override fun read(`in`: JsonReader?): DateOnly? = `in`?.let { reader ->
        LoggingExceptionHandler.runCatching(defaultValue = null) {
            if (reader.peek() === JsonToken.NULL) {
                reader.nextNull()
                return@runCatching null
            }

            reader.nextString()?.let { formatter.parse(it)?.time?.let(::DateOnly) }
        }
    }
}
