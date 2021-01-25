package cloud.mindbox.mobile_sdk.models

import okhttp3.ResponseBody
import java.util.*


data class InitResponse(
    var message: String? = null,
    var status: String? = null
)

sealed class MindboxResponse {

    data class SuccessResponse<T>(
        var body: T
    ) : MindboxResponse()

    data class Error(
        var status: Int? = null,
        var data: ByteArray
    ) : MindboxResponse() {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Error

            if (status != other.status) return false
            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = status ?: 0
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    data class ValidationError(
        var messages: List<String> = emptyList()
    ) : MindboxResponse() {

        companion object {
            private const val ERROR_INVALID_DOMAIN = "The domain must not start with https:// and must not end with /"
            private const val ERROR_EMPTY_ENDPOINT = "Endpoint must not be empty"
            private const val ERROR_INVALID_DEVICE_ID = "Invalid device UUID format"
        }

        fun validateFields(domain: String, endpoint: String, deviceId: String) {
            val errors = arrayListOf<String>()

            if (domain.startsWith("http") || domain.startsWith("/") || domain.endsWith("/")) {
                errors.add(ERROR_INVALID_DOMAIN)
            }

            if (endpoint.trim().isEmpty()) {
                errors.add(ERROR_EMPTY_ENDPOINT)
            }

            try {
                UUID.fromString(deviceId)
            } catch (e: Exception) {
                errors.add(ERROR_INVALID_DEVICE_ID)
            }

            this.messages = errors.toList()
        }
    }
}