package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.models.AsyncEventSendResult
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.PushDelivery
import cloud.mindbox.mobile_sdk.models.UrlQuery
import cloud.mindbox.mobile_sdk.network.MindboxServiceGenerator
import cloud.mindbox.mobile_sdk.network.PushDeliveredRequest
import cloud.mindbox.mobile_sdk.pushes.DeliveredUrl
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import com.android.volley.VolleyError
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

internal class PushDeliveredManager(
    private val mindboxServiceGenerator: MindboxServiceGenerator,
    private val timeProvider: TimeProvider,
) {

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429
        val HTTP_SERVER_ERRORS = 500..599
        val ATTEMPT_DEADLINE = Milliseconds(2_500L)
    }

    suspend fun send(
        configuration: Configuration,
        delivery: PushDelivery,
    ): AsyncEventSendResult {
        val url = DeliveredUrl.of(delivery.url)
        if (url == null) {
            mindboxLogW("Push delivery #${delivery.id} has no delivered URL, dropping it")
            return AsyncEventSendResult.DISCARDED
        }
        val offset = delivery.elapsedAt(timeProvider.currentTimestamp())
        val result = withTimeoutOrNull(ATTEMPT_DEADLINE.interval.milliseconds) {
            execute(
                url = url.withQueryParameter(UrlQuery.DATE_TIME_OFFSET.value, offset.interval.toString()),
                configuration = configuration,
            )
        }
        if (result == null) {
            mindboxLogI("[GD] push delivered decision: no answer in ${ATTEMPT_DEADLINE.interval} ms → keep")
        }
        return result ?: AsyncEventSendResult.RETRY_LATER
    }

    private suspend fun execute(
        url: String,
        configuration: Configuration,
    ): AsyncEventSendResult = suspendCancellableCoroutine { continuation ->
        val request = PushDeliveredRequest(
            url = url,
            configuration = configuration,
            timeout = ATTEMPT_DEADLINE,
            onDelivered = {
                loggingRunCatching { if (continuation.isActive) continuation.resume(AsyncEventSendResult.DELIVERED) }
            },
            errorListener = { volleyError ->
                loggingRunCatching { if (continuation.isActive) continuation.resume(resultOf(volleyError)) }
            },
        )
        continuation.invokeOnCancellation { request.cancel() }
        mindboxServiceGenerator.addToRequestQueue(request)
    }

    private fun resultOf(volleyError: VolleyError): AsyncEventSendResult {
        val statusCode = volleyError.networkResponse?.statusCode
        val result = when (statusCode) {
            null -> AsyncEventSendResult.RETRY_LATER
            HTTP_TOO_MANY_REQUESTS -> AsyncEventSendResult.THROTTLED
            in HTTP_SERVER_ERRORS -> AsyncEventSendResult.RETRY_LATER
            else -> AsyncEventSendResult.DISCARDED
        }
        val response = statusCode?.let { "HTTP $it" } ?: "no response"
        val decision = if (result.isRemovedFromQueue) "delete" else "keep"
        mindboxLogI("[GD] push delivered decision: $response → $decision")
        return result
    }
}
