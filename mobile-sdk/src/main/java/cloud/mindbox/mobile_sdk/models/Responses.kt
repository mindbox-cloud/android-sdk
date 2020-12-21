package cloud.mindbox.mobile_sdk.models

import okhttp3.ResponseBody


data class InitResponse(
    var message: String? = null,
    var status: String? = null
)

sealed class MindboxResponse {

    data class SuccessResponse<T>(
        var status: Int? = null,
        val body: T
    ) : MindboxResponse()

    data class Error(
        val status: Int? = null,
        val message: String,
        val errorBody: ResponseBody?
    ) : MindboxResponse()
}