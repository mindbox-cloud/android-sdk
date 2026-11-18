package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.models.AsyncEventSendResult
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.UrlQuery
import cloud.mindbox.mobile_sdk.models.dateTimeOffset
import cloud.mindbox.mobile_sdk.network.MindboxServiceGenerator
import cloud.mindbox.mobile_sdk.network.PushDeliveredRequest
import cloud.mindbox.mobile_sdk.pushes.DeliveredUrl
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import com.android.volley.VolleyError

internal class PushDeliveredManager(
    private val mindboxServiceGenerator: MindboxServiceGenerator,
    private val timeProvider: TimeProvider,
) {

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429
        val HTTP_SERVER_ERRORS = 500..599
    }

    fun send(
        configuration: Configuration,
        event: Event,
        shouldCountOffset: Boolean,
        onResult: (AsyncEventSendResult) -> Unit,
    ) {
        val url = DeliveredUrl.of(event.additionalFields?.get(EventType.PushDelivered.FIELD_URL))
        if (url == null) {
            mindboxLogW("Event #${event.uid} has no delivered URL, dropping it")
            onResult(AsyncEventSendResult.DISCARDED)
            return
        }
        try {
            val offset = event.dateTimeOffset(shouldCountOffset, timeProvider.currentTimestamp())
            val request = PushDeliveredRequest(
                url = url.withQueryParameter(UrlQuery.DATE_TIME_OFFSET.value, offset.interval.toString()),
                configuration = configuration,
                onDelivered = { onResult(AsyncEventSendResult.DELIVERED) },
                errorListener = { volleyError -> onResult(resultOf(volleyError)) },
            )
            mindboxServiceGenerator.addToRequestQueue(request)
        } catch (e: Exception) {
            mindboxLogE("Sending push delivered event #${event.uid} failed", e)
            onResult(AsyncEventSendResult.DISCARDED)
        }
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
