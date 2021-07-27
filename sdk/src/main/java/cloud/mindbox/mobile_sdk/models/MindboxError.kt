package cloud.mindbox.mobile_sdk.models

sealed class MindboxError(open val statusCode: Int?) {

    data class Validation(
        override val statusCode: Int,
        val status: String,
        val validationMessages: List<ValidationMessage>
    ) : MindboxError(statusCode)

    data class Protocol(
        override val statusCode: Int,
        val status: String,
        val errorMessage: String?,
        val errorId: String?,
        val httpStatusCode: Int?
    ) : MindboxError(statusCode)

    data class InternalServer(
        override val statusCode: Int,
        val status: String,
        val errorMessage: String?,
        val errorId: String?,
        val httpStatusCode: Int?
    ) : MindboxError(statusCode)

    data class UnknownServer(
        override val statusCode: Int? = null,
        val status: String? = null,
        val errorMessage: String? = null,
        val errorId: String? = null,
        val httpStatusCode: Int? = null
    ) : MindboxError(statusCode)

    data class Unknown(
        val throwable: Throwable? = null
    ) : MindboxError(null)

}
