package cloud.mindbox.mobile_sdk.models.operation.adapters

import cloud.mindbox.mobile_sdk.models.operation.request.DateOnlyRequest
import cloud.mindbox.mobile_sdk.returnOnException
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.text.SimpleDateFormat
import java.util.*

class DateOnlyRequestAdapter : TypeAdapter<DateOnlyRequest>() {

    private val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun write(out: JsonWriter?, value: DateOnlyRequest?) {
        runCatching {
            if (value == null) {
                out?.nullValue()
            } else {
                out?.value(formatter.format(value))
            }
        }.returnOnException { out }
    }

    override fun read(`in`: JsonReader?): DateOnlyRequest? = `in`?.let { reader ->
        runCatching {
            if (reader.peek() === JsonToken.NULL) {
                reader.nextNull()
                return@let null
            }

            reader.nextString()?.let { formatter.parse(it)?.time?.let(::DateOnlyRequest) }
        }.returnOnException { null }
    }

}
