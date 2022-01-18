package cloud.mindbox.mobile_sdk_core.models

import com.google.gson.Gson
import com.google.gson.annotations.JsonAdapter

sealed class MindboxErrorInternal(open val statusCode: Int?) {

    companion object {

        private val gson by lazy { Gson() }

    }

    fun toJson(): String = gson.toJson(this)

    @JsonAdapter(MindboxErrorAdapter::class)
    data class Validation(
        override val statusCode: Int,
        val status: String,
        val validationMessages: List<ValidationMessageInternal>
    ) : MindboxErrorInternal(statusCode)

    @JsonAdapter(MindboxErrorAdapter::class)
    data class Protocol(
        override val statusCode: Int,
        val status: String,
        val errorMessage: String?,
        val errorId: String?,
        val httpStatusCode: Int?
    ) : MindboxErrorInternal(statusCode)

    @JsonAdapter(MindboxErrorAdapter::class)
    data class InternalServer(
        override val statusCode: Int,
        val status: String,
        val errorMessage: String?,
        val errorId: String?,
        val httpStatusCode: Int?
    ) : MindboxErrorInternal(statusCode)

    @JsonAdapter(MindboxErrorAdapter::class)
    data class UnknownServer(
        override val statusCode: Int? = null,
        val status: String? = null,
        val errorMessage: String? = null,
        val errorId: String? = null,
        val httpStatusCode: Int? = null
    ) : MindboxErrorInternal(statusCode) {

        constructor() : this(errorMessage = "Cannot reach server")

    }

    @JsonAdapter(MindboxErrorAdapter::class)
    data class Unknown(
        val throwable: Throwable? = null
    ) : MindboxErrorInternal(null)

}
