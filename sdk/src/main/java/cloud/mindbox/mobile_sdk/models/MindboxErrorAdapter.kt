package cloud.mindbox.mobile_sdk.models

import cloud.mindbox.mobile_sdk.returnOnException
import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

class MindboxErrorAdapter : TypeAdapter<MindboxError?>() {

    private val gson by lazy { Gson() }

    private val errorJsonNames = mapOf(
        MindboxError.Validation::class to "ValidationError",
        MindboxError.Protocol::class to "ProtocolError",
        MindboxError.InternalServer::class to "InternalError",
        MindboxError.UnknownServer::class to "UnknownServerError",
        MindboxError.Unknown::class to "UnknownError"
    )

    override fun write(out: JsonWriter?, value: MindboxError?) {
        if (value == null) {
            out?.nullValue()
        } else {
            out?.beginObject()
                ?.name("type")
                ?.value(errorJsonNames[value::class])
                ?.name("data")
                ?.writeErrorObject(value)
                ?.endObject()
        }
    }

    override fun read(`in`: JsonReader?): MindboxError? = `in`?.let { reader ->
        runCatching {
            reader.beginObject()
            val error = when (reader.nextString()) {
                "ValidationError" -> MindboxError.Validation(
                    statusCode = reader.nextInt(),
                    status = reader.nextString(),
                    validationMessages = gson.fromJson(
                        reader,
                        object : TypeToken<List<ValidationMessage>>() {}.type
                    )
                )
                "ProtocolError" -> MindboxError.Protocol(
                    statusCode = reader.nextInt(),
                    status = reader.nextString(),
                    errorMessage = if (reader.peek() == JsonToken.STRING) reader.nextString() else null,
                    errorId = if (reader.peek() == JsonToken.STRING) reader.nextString() else null,
                    httpStatusCode = if (reader.peek() == JsonToken.NUMBER) reader.nextInt() else null,
                )
                "InternalError" -> MindboxError.InternalServer(
                    statusCode = reader.nextInt(),
                    status = reader.nextString(),
                    errorMessage = if (reader.peek() == JsonToken.STRING) reader.nextString() else null,
                    errorId = if (reader.peek() == JsonToken.STRING) reader.nextString() else null,
                    httpStatusCode = if (reader.peek() == JsonToken.NUMBER) reader.nextInt() else null,
                )
                "UnknownServerError" -> MindboxError.UnknownServer(
                    statusCode = reader.nextInt(),
                    status = if (reader.peek() == JsonToken.STRING) reader.nextString() else null,
                    errorMessage = if (reader.peek() == JsonToken.STRING) reader.nextString() else null,
                    errorId = if (reader.peek() == JsonToken.STRING) reader.nextString() else null,
                    httpStatusCode = if (reader.peek() == JsonToken.NUMBER) reader.nextInt() else null
                )
                "UnknownError" -> MindboxError.Unknown()
                else -> null
            }
            reader.endObject()
            error
        }.returnOnException { null }
    }

    private fun JsonWriter.writeErrorObject(value: MindboxError) = beginObject().apply {
        when (value) {
            is MindboxError.Validation -> name("statusCode").value(value.statusCode)
                .name("status").value(value.status)
                .name("validationMessages").jsonValue(gson.toJson(value.validationMessages))
            is MindboxError.Protocol -> name("statusCode").value(value.statusCode)
                .name("status").value(value.status)
                .name("errorMessage").value(value.errorMessage)
                .name("errorId").value(value.errorId)
                .name("httpStatusCode").value(value.httpStatusCode)
            is MindboxError.InternalServer -> name("statusCode").value(value.statusCode)
                .name("status").value(value.status)
                .name("errorMessage").value(value.errorMessage)
                .name("errorId").value(value.errorId)
                .name("httpStatusCode").value(value.httpStatusCode)
            is MindboxError.UnknownServer -> name("statusCode").value(value.statusCode)
                .name("status").value(value.status)
                .name("errorMessage").value(value.errorMessage)
                .name("errorId").value(value.errorId)
                .name("httpStatusCode").value(value.httpStatusCode)
            is MindboxError.Unknown -> name("statusCode").value(value.statusCode)
                .name("name").value(value.throwable?.javaClass?.canonicalName)
                .name("message").value(value.throwable?.localizedMessage)
        }
    }
        .endObject()
}