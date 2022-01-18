package cloud.mindbox.mobile_sdk_core.models

import cloud.mindbox.mobile_sdk_core.returnOnException
import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

class MindboxErrorAdapter : TypeAdapter<MindboxErrorInternal?>() {

    private val gson by lazy { Gson() }

    private val errorJsonNames = mapOf(
        MindboxErrorInternal.Validation::class to "MindboxError",
        MindboxErrorInternal.Protocol::class to "MindboxError",
        MindboxErrorInternal.InternalServer::class to "MindboxError",
        MindboxErrorInternal.UnknownServer::class to "NetworkError",
        MindboxErrorInternal.Unknown::class to "InternalError"
    )

    override fun write(out: JsonWriter?, value: MindboxErrorInternal?) {
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

    override fun read(`in`: JsonReader?): MindboxErrorInternal? = `in`?.let { reader ->
        runCatching {
            reader.beginObject()
            val error = when (reader.nextString()) {
                "MindboxError" -> {
                    val statusCode = reader.nextInt()
                    when (statusCode) {
                        200 -> MindboxErrorInternal.Validation(
                            statusCode = reader.nextInt(),
                            status = reader.nextString(),
                            validationMessages = gson.fromJson(reader, object : TypeToken<List<ValidationMessageInternal>>() {}.type)
                        )
                        400, 401, 403, 429 -> MindboxErrorInternal.Protocol(
                            statusCode = reader.nextInt(),
                            status = reader.nextString(),
                            errorMessage = if (reader.peek() == JsonToken.STRING) reader.nextString() else null,
                            errorId = if (reader.peek() == JsonToken.STRING) reader.nextString() else null,
                            httpStatusCode = if (reader.peek() == JsonToken.NUMBER) reader.nextInt() else null,
                        )
                        500, 503 -> MindboxErrorInternal.InternalServer(
                            statusCode = reader.nextInt(),
                            status = reader.nextString(),
                            errorMessage = if (reader.peek() == JsonToken.STRING) reader.nextString() else null,
                            errorId = if (reader.peek() == JsonToken.STRING) reader.nextString() else null,
                            httpStatusCode = if (reader.peek() == JsonToken.NUMBER) reader.nextInt() else null,
                        )
                        else -> null
                    }
                }
                "NetworkError" -> MindboxErrorInternal.UnknownServer(
                    statusCode = reader.nextInt(),
                    status = if (reader.peek() == JsonToken.STRING) reader.nextString() else null,
                    errorMessage = if (reader.peek() == JsonToken.STRING) reader.nextString() else null,
                    errorId = if (reader.peek() == JsonToken.STRING) reader.nextString() else null,
                    httpStatusCode = if (reader.peek() == JsonToken.NUMBER) reader.nextInt() else null
                )
                "InternalError" -> MindboxErrorInternal.Unknown().apply { reader.skipValue() }
                else -> null
            }
            reader.endObject()
            error
        }.returnOnException { null }
    }

    private fun JsonWriter.writeErrorObject(value: MindboxErrorInternal) = beginObject().apply {
        when (value) {
            is MindboxErrorInternal.Validation -> name("statusCode").value(value.statusCode)
                .name("status").value(value.status)
                .name("validationMessages").jsonValue(gson.toJson(value.validationMessages))
            is MindboxErrorInternal.Protocol -> name("statusCode").value(value.statusCode)
                .name("status").value(value.status)
                .name("errorMessage").value(value.errorMessage)
                .name("errorId").value(value.errorId)
                .name("httpStatusCode").value(value.httpStatusCode)
            is MindboxErrorInternal.InternalServer -> name("statusCode").value(value.statusCode)
                .name("status").value(value.status)
                .name("errorMessage").value(value.errorMessage)
                .name("errorId").value(value.errorId)
                .name("httpStatusCode").value(value.httpStatusCode)
            is MindboxErrorInternal.UnknownServer -> name("statusCode").value(value.statusCode)
                .name("status").value(value.status)
                .name("errorMessage").value(value.errorMessage)
                .name("errorId").value(value.errorId)
                .name("httpStatusCode").value(value.httpStatusCode)
            is MindboxErrorInternal.Unknown -> name("errorName").value(value.throwable?.javaClass?.canonicalName)
                .name("errorMessage").value(value.throwable?.localizedMessage)
        }
    }
        .endObject()
}