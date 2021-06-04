package cloud.mindbox.mobile_sdk.models

sealed class MindboxError(
    open val status: String/*,
    open val statusCode: Int*/
) {

    data class Validation(
        override val status: String,
        val validationMessages: List<ValidationMessage>
    ) : MindboxError(status)

    data class Protocol(
        override val status: String,
        val errorMessage: String?,
        val errorId: String?,
        val httpStatusCode: Int?
    ) : MindboxError(status)

    data class InternalServer(
        override val status: String,
        val errorMessage: String?,
        val errorId: String?,
        val httpStatusCode: Int?
    ) : MindboxError(status)

    data class Unknown(
        override val status: String,
        val errorMessage: String? = null,
        val errorId: String? = null,
        val httpStatusCode: Int? = null
    ) : MindboxError(status)

}

data class TestError(
    val status: String? = null,
    val errorMessage: String? = null,
    val errorId: String? = null,
    val httpStatusCode: Int? = null,
    val validationMessages: List<ValidationMessage> ? = null
)
