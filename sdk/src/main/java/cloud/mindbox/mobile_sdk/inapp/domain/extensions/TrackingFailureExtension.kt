package cloud.mindbox.mobile_sdk.inapp.domain.extensions

import cloud.mindbox.mobile_sdk.getErrorResponseBodyData
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFailureTracker
import cloud.mindbox.mobile_sdk.inapp.domain.models.TargetingData
import cloud.mindbox.mobile_sdk.models.operation.request.OperationBodyRequest
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import com.android.volley.TimeoutError
import com.android.volley.VolleyError
import com.google.gson.Gson
import java.net.SocketTimeoutException
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason

internal fun VolleyError.isTimeoutError(): Boolean {
    return this is TimeoutError || cause is SocketTimeoutException
}

internal fun VolleyError.isServerError(): Boolean {
    val statusCode = networkResponse?.statusCode ?: return false
    return statusCode in 500..599
}

internal fun Throwable?.asVolleyError(): VolleyError? = this as? VolleyError

internal fun Throwable.getVolleyErrorDetails(): String {
    val volleyError = this.asVolleyError() ?: return "volleyError = null"
    val statusCode = volleyError.networkResponse?.statusCode ?: "timeout error"
    val networkTimeMs = volleyError.networkTimeMs
    val body = volleyError.getErrorResponseBodyData()
    return "statusCode=$statusCode, networkTimeMs=$networkTimeMs, body=$body"
}

internal fun TargetingData.getProductFromTargetingData(): Pair<String, String>? {
    if (this !is TargetingData.OperationBody) return null
    return parseOperationBody(this.operationBody)
}

private fun parseOperationBody(operationBody: String?): Pair<String, String>? =
    loggingRunCatching(null) {
        val body = Gson().fromJson(operationBody, OperationBodyRequest::class.java) ?: return@loggingRunCatching null
        body.viewProductRequest
            ?.product
            ?.ids
            ?.ids
            ?.entries
            ?.firstOrNull()
            ?.takeIf { entry ->
                entry.value?.isNotBlank() == true
            }
            ?.let { entry -> entry.key to entry.value!! }
    }

internal fun Throwable.shouldTrackTargetingError(): Boolean {
    return this.cause.asVolleyError()?.let { volleyError ->
        volleyError.isTimeoutError() || volleyError.isServerError()
    } ?: false
}

internal fun InAppFailureTracker.sendPresentationFailure(
    inAppId: String,
    errorDescription: String,
    throwable: Throwable? = null
) {
    val errorDetails = when {
        throwable != null -> "$errorDescription: ${throwable.message ?: "Unknown error"}"
        else -> errorDescription
    }
    mindboxLogE(errorDetails)
    sendFailure(
        inAppId = inAppId,
        failureReason = FailureReason.PRESENTATION_FAILED,
        errorDetails = errorDetails
    )
}

internal fun InAppFailureTracker.sendFailureWithContext(
    inAppId: String,
    failureReason: FailureReason,
    errorDescription: String,
    throwable: Throwable? = null
) {
    val errorDetails = when {
        throwable != null -> "$errorDescription: ${throwable.message ?: "Unknown error"}"
        else -> errorDescription
    }
    mindboxLogE(errorDetails)
    sendFailure(
        inAppId = inAppId,
        failureReason = failureReason,
        errorDetails = errorDetails
    )
}

internal inline fun <T> InAppFailureTracker.executeWithFailureTracking(
    inAppId: String,
    failureReason: FailureReason,
    errorDescription: String,
    crossinline onFailure: () -> Unit = {},
    block: () -> T
): Result<T> {
    return runCatching(block).onFailure { throwable ->
        sendFailureWithContext(
            inAppId = inAppId,
            failureReason = failureReason,
            errorDescription = errorDescription,
            throwable = throwable
        )
        onFailure()
    }
}
