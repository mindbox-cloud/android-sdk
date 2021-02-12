package cloud.mindbox.mobile_sdk.models

sealed class MindboxResponse {

    data class SuccessResponse<T>(
        var body: T
    ) : MindboxResponse()

    data class BadRequest(
        var status: Int? = null
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
}