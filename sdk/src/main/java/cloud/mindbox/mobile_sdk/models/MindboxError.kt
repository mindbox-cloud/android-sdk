package cloud.mindbox.mobile_sdk.models

import com.google.gson.Gson
import com.google.gson.annotations.JsonAdapter
/**
 * A class for representing Mindbox error.
 * Used in operations.
 * */
sealed class MindboxError(open val statusCode: Int?) {

    companion object {

        private val gson by lazy { Gson() }

    }

    fun toJson(): String = gson.toJson(this)

    @JsonAdapter(MindboxErrorAdapter::class)
    data class Validation(
        override val statusCode: Int,
        val status: String,
        val validationMessages: List<ValidationMessage>,
    ) : MindboxError(statusCode)

    @JsonAdapter(MindboxErrorAdapter::class)
    data class Protocol(
        override val statusCode: Int,
        val status: String,
        val errorMessage: String?,
        val errorId: String?,
        val httpStatusCode: Int?,
    ) : MindboxError(statusCode)

    @JsonAdapter(MindboxErrorAdapter::class)
    data class InternalServer(
        override val statusCode: Int,
        val status: String,
        val errorMessage: String?,
        val errorId: String?,
        val httpStatusCode: Int?,
    ) : MindboxError(statusCode)

    @JsonAdapter(MindboxErrorAdapter::class)
    data class UnknownServer(
        override val statusCode: Int? = null,
        val status: String? = null,
        val errorMessage: String? = null,
        val errorId: String? = null,
        val httpStatusCode: Int? = null,
    ) : MindboxError(statusCode) {

        constructor() : this(errorMessage = "Cannot reach server")

    }

    @JsonAdapter(MindboxErrorAdapter::class)
    data class Unknown(val throwable: Throwable? = null) : MindboxError(null)

}
