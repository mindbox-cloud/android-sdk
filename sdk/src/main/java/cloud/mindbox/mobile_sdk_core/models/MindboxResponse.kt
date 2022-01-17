package cloud.mindbox.mobile_sdk_core.models

import com.google.gson.annotations.SerializedName

internal class MindboxResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("errorMessage") val errorMessage: String? = null,
    @SerializedName("errorId") val errorId: String? = null,
    @SerializedName("httpStatusCode") val httpStatusCode: Int? = null,
    @SerializedName("validationMessages") val validationMessages: List<ValidationMessage>? = null
) {

    companion object {

        internal const val STATUS_SUCCESS = "Success"
        internal const val STATUS_TRANSACTION_ALREADY_PROCESSED = "TransactionAlreadyProcessed"
        internal const val STATUS_VALIDATION_ERROR = "ValidationError"
        internal const val STATUS_PROTOCOL_ERROR = "ProtocolError"
        internal const val STATUS_INTERNAL_SERVER_ERROR = "InternalServerError"

    }

}
