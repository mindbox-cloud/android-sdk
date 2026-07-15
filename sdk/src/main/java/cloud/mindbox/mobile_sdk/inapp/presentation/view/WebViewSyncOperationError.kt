package cloud.mindbox.mobile_sdk.inapp.presentation.view

import cloud.mindbox.mobile_sdk.models.MindboxError
import com.google.gson.Gson
import com.google.gson.JsonObject

internal class WebViewSyncOperationException(val payloadJson: String) : Exception(payloadJson)

/**
 * Data-only JSON without the `{type, data}` envelope — the WebView JS-bridge `onError`
 * contract shared with iOS: string `httpStatusCode`, no transport `statusCode`.
 * `toJson()` must keep the envelope: RN/Flutter wrappers dispatch on it.
 *
 * Serialized via `JsonElement.toString()`, not `gson.toJson`: the SDK gson has
 * htmlSafe enabled and would escape `<`/`&`/`'`, while iOS `JSONEncoder` does not.
 */
internal fun MindboxError.toWebViewDataJson(gson: Gson): String {
    val data = JsonObject()
    when (this) {
        is MindboxError.Validation -> {
            data.addProperty("status", status)
            data.add("validationMessages", gson.toJsonTree(validationMessages))
        }

        is MindboxError.Protocol ->
            data.addServerErrorFields(status, errorMessage, errorId, httpStatusCode)

        is MindboxError.InternalServer ->
            data.addServerErrorFields(status, errorMessage, errorId, httpStatusCode)

        is MindboxError.UnknownServer -> {
            status?.let { data.addProperty("status", it) }
            errorMessage?.let { data.addProperty("errorMessage", it) }
            errorId?.let { data.addProperty("errorId", it) }
            data.addProperty("httpStatusCode", httpStatusCode?.toString() ?: "null")
        }

        is MindboxError.Unknown -> {
            data.addProperty("errorKey", "unknown")
            data.addProperty("errorName", throwable?.javaClass?.canonicalName ?: "")
            data.addProperty("errorMessage", throwable?.localizedMessage ?: "")
        }
    }
    return data.toString()
}

private fun JsonObject.addServerErrorFields(
    status: String,
    errorMessage: String?,
    errorId: String?,
    httpStatusCode: Int?,
) {
    addProperty("status", status)
    errorMessage?.let { addProperty("errorMessage", it) }
    addProperty("errorId", errorId ?: "")
    addProperty("httpStatusCode", httpStatusCode?.toString() ?: "null")
}
