package cloud.mindbox.mobile_sdk.models

import com.google.gson.Gson
import com.google.gson.annotations.JsonAdapter

/**
 * A class for representing Mindbox error.
 * Used in operations.
 * */
public sealed class MindboxError(public open val statusCode: Int?) {

    private companion object {

        private val gson by lazy { Gson() }
    }

    public fun toJson(): String = gson.toJson(this)

    @JsonAdapter(MindboxErrorAdapter::class)
    public data class Validation(
        override val statusCode: Int,
        val status: String,
        val validationMessages: List<ValidationMessage>,
    ) : MindboxError(statusCode)

    @JsonAdapter(MindboxErrorAdapter::class)
    public data class Protocol(
        override val statusCode: Int,
        val status: String,
        val errorMessage: String?,
        val errorId: String?,
        val httpStatusCode: Int?,
    ) : MindboxError(statusCode)

    @JsonAdapter(MindboxErrorAdapter::class)
    public data class InternalServer(
        override val statusCode: Int,
        val status: String,
        val errorMessage: String?,
        val errorId: String?,
        val httpStatusCode: Int?,
    ) : MindboxError(statusCode)

    @JsonAdapter(MindboxErrorAdapter::class)
    public data class UnknownServer(
        override val statusCode: Int? = null,
        val status: String? = null,
        val errorMessage: String? = null,
        val errorId: String? = null,
        val httpStatusCode: Int? = null,
    ) : MindboxError(statusCode) {

        public constructor() : this(errorMessage = "Cannot reach server")
    }

    @JsonAdapter(MindboxErrorAdapter::class)
    public data class Unknown(val throwable: Throwable? = null) : MindboxError(null)
}
