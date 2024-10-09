package cloud.mindbox.mobile_sdk.utils

import com.google.gson.JsonSyntaxException
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.IOException

class StrictStringAdapter : TypeAdapter<String?>() {
    @Throws(IOException::class)
    override fun read(reader: JsonReader): String? {
        return when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }

            JsonToken.STRING -> reader.nextString()
            else -> throw JsonSyntaxException("Expected STRING but was " + reader.peek())
        }
    }

    @Throws(IOException::class)
    override fun write(out: JsonWriter, value: String?) {
        out.value(value)
    }
}
