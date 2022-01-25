package cloud.mindbox.mobile_sdk.models

import cloud.mindbox.mobile_sdk_core.models.MindboxErrorInternal
import com.google.gson.Gson
import com.google.gson.annotations.JsonAdapter

sealed class MindboxError(open val statusCode: Int?) {

    companion object {

        private val gson by lazy { Gson() }

        internal fun fromInternal(error: MindboxErrorInternal) = when (error) {
            is MindboxErrorInternal.InternalServer -> InternalServer(
                statusCode = error.statusCode,
                status = error.status,
                errorMessage = error.errorMessage,
                errorId = error.errorId,
                httpStatusCode = error.httpStatusCode
            )
            is MindboxErrorInternal.Protocol -> Protocol(
                statusCode = error.statusCode,
                status = error.status,
                errorMessage = error.errorMessage,
                errorId = error.errorId,
                httpStatusCode = error.httpStatusCode
            )
            is MindboxErrorInternal.Unknown -> Unknown(
                throwable = error.throwable
            )
            is MindboxErrorInternal.UnknownServer -> UnknownServer(
                statusCode = error.statusCode,
                status = error.status,
                errorMessage = error.errorMessage,
                errorId = error.errorId,
                httpStatusCode = error.httpStatusCode
            )
            is MindboxErrorInternal.Validation -> Validation(
                statusCode = error.statusCode,
                status = error.status,
                validationMessages = error.validationMessages
                    .map(ValidationMessage.Companion::fromInternal)
            )
        }

    }

    fun toJson(): String = gson.toJson(this)

    @JsonAdapter(MindboxErrorAdapter::class)
    data class Validation(
        override val statusCode: Int,
        val status: String,
        val validationMessages: List<ValidationMessage>
    ) : MindboxError(statusCode)

    @JsonAdapter(MindboxErrorAdapter::class)
    data class Protocol(
        override val statusCode: Int,
        val status: String,
        val errorMessage: String?,
        val errorId: String?,
        val httpStatusCode: Int?
    ) : MindboxError(statusCode)

    @JsonAdapter(MindboxErrorAdapter::class)
    data class InternalServer(
        override val statusCode: Int,
        val status: String,
        val errorMessage: String?,
        val errorId: String?,
        val httpStatusCode: Int?
    ) : MindboxError(statusCode)

    @JsonAdapter(MindboxErrorAdapter::class)
    data class UnknownServer(
        override val statusCode: Int? = null,
        val status: String? = null,
        val errorMessage: String? = null,
        val errorId: String? = null,
        val httpStatusCode: Int? = null
    ) : MindboxError(statusCode) {

        constructor() : this(errorMessage = "Cannot reach server")

    }

    @JsonAdapter(MindboxErrorAdapter::class)
    data class Unknown(
        val throwable: Throwable? = null
    ) : MindboxError(null)

}
