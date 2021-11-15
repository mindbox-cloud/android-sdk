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
        MindboxError.Validation::class to "MindboxError",
        MindboxError.Protocol::class to "MindboxError",
        MindboxError.InternalServer::class to "MindboxError",
        MindboxError.UnknownServer::class to "NetworkError",
        MindboxError.Unknown::class to "InternalError"
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
                "MindboxError" -> {
                    val statusCode = reader.nextInt()
                    when (statusCode) {
                        200 -> MindboxError.Validation(
                            statusCode = reader.nextInt(),
                            status = reader.nextString(),
                            validationMessages = gson.fromJson(reader, object : TypeToken<List<ValidationMessage>>() {}.type)
                        )
                        400, 401, 403, 429 -> MindboxError.Protocol(
                            statusCode = reader.nextInt(),
                            status = reader.nextString(),
                            errorMessage = if (reader.peek() == JsonToken.STRING) reader.nextString() else null,
                            errorId = if (reader.peek() == JsonToken.STRING) reader.nextString() else null,
                            httpStatusCode = if (reader.peek() == JsonToken.NUMBER) reader.nextInt() else null,
                        )
                        500, 503 -> MindboxError.InternalServer(
                            statusCode = reader.nextInt(),
                            status = reader.nextString(),
                            errorMessage = if (reader.peek() == JsonToken.STRING) reader.nextString() else null,
                            errorId = if (reader.peek() == JsonToken.STRING) reader.nextString() else null,
                            httpStatusCode = if (reader.peek() == JsonToken.NUMBER) reader.nextInt() else null,
                        )
                        else -> null
                    }
                }
                "NetworkError" -> MindboxError.UnknownServer(
                    statusCode = reader.nextInt(),
                    status = if (reader.peek() == JsonToken.STRING) reader.nextString() else null,
                    errorMessage = if (reader.peek() == JsonToken.STRING) reader.nextString() else null,
                    errorId = if (reader.peek() == JsonToken.STRING) reader.nextString() else null,
                    httpStatusCode = if (reader.peek() == JsonToken.NUMBER) reader.nextInt() else null
                )
                "InternalError" -> MindboxError.Unknown().apply { reader.skipValue() }
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
            is MindboxError.Unknown -> name("name").value(value.throwable?.javaClass?.canonicalName)
                .name("message").value(value.throwable?.localizedMessage)
        }
    }
        .endObject()
}