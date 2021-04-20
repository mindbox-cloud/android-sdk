package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.models.*
import cloud.mindbox.mobile_sdk.network.MindboxServiceGenerator
import cloud.mindbox.mobile_sdk.toUrlQueryString
import com.android.volley.DefaultRetryPolicy
import com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
import com.android.volley.NetworkResponse
import com.android.volley.Request
import org.json.JSONException
import org.json.JSONObject
import java.util.*

internal object GatewayManager {

    private const val TIMEOUT_DELAY = 60000
    private const val MAX_RETRIES = 0

    private fun buildEventUrl(
        configuration: MindboxConfiguration,
        deviceUuid: String,
        event: Event
    ): String {

        val urlQueries: HashMap<String, String> = hashMapOf(
            UrlQuery.DEVICE_UUID.value to deviceUuid,
            UrlQuery.TRANSACTION_ID.value to event.transactionId,
            UrlQuery.DATE_TIME_OFFSET.value to getTimeOffset(event.enqueueTimestamp)
        )

        when (event.eventType) {
            is EventType.AppInstalled,
            is EventType.AppInfoUpdated,
            is EventType.PushClicked -> {
                urlQueries[UrlQuery.ENDPOINT_ID.value] = configuration.endpointId
                urlQueries[UrlQuery.OPERATION.value] = event.eventType.operation
            }
            is EventType.PushDelivered -> {
                urlQueries[UrlQuery.ENDPOINT_ID.value] = configuration.endpointId
                urlQueries[UrlQuery.UNIQ_KEY.value] =
                    event.additionalFields?.get(EventParameters.UNIQ_KEY.fieldName) ?: ""
            }
            is EventType.AsyncOperation -> {
                urlQueries[UrlQuery.ENDPOINT_ID.value] = configuration.endpointId
                urlQueries[UrlQuery.OPERATION.value] = event.eventType.operation
            }
        }

        return "https://${configuration.domain}${event.eventType.endpoint}${urlQueries.toUrlQueryString()}"
    }

    fun sendEvent(
        context: Context,
        configuration: MindboxConfiguration,
        deviceUuid: String,
        event: Event,
        isSuccess: (Boolean) -> Unit
    ) {
        try {

            val requestType: Int = getRequestType(event.eventType)
            val url: String = buildEventUrl(configuration, deviceUuid, event)
            val jsonRequest: JSONObject? = convertBodyToJson(event.body)

            val request = MindboxRequest(requestType, url, configuration, jsonRequest,
                {
                    MindboxLogger.d(this, "Event from background successful sent")
                    isSuccess.invoke(true)
                }, { volleyError ->
                    try {
                        when (val result = parseResponse(volleyError.networkResponse)) {
                            is MindboxResponse.SuccessResponse<*>,
                            is MindboxResponse.BadRequest -> {
                                MindboxLogger.d(this, "Event from background successful sent")
                                isSuccess.invoke(true)
                            }
                            is MindboxResponse.Error -> {
                                MindboxLogger.d(
                                    this,
                                    "Sending event from background was failure with code ${result.status}"
                                )
                                isSuccess.invoke(false)
                            }
                        }
                    } catch (e: Exception) {
                        MindboxLogger.e(this, "Parsing server response was failure", e)
                        isSuccess.invoke(false)
                    }
                }
            ).apply {
                setShouldCache(false)
                retryPolicy = DefaultRetryPolicy(TIMEOUT_DELAY, MAX_RETRIES, DEFAULT_BACKOFF_MULT)
            }

            MindboxServiceGenerator.getInstance(context)?.addToRequestQueue(request)
        } catch (e: Exception) {
            MindboxLogger.e(this, "Sending event was failure with exception", e)
            isSuccess.invoke(false)
        }
    }

    private fun getRequestType(eventType: EventType): Int {
        return when (eventType) {
            is EventType.AppInstalled,
            is EventType.AppInfoUpdated,
            is EventType.PushClicked,
            is EventType.TrackVisit,
            is EventType.AsyncOperation -> Request.Method.POST
            is EventType.PushDelivered -> Request.Method.GET
        }
    }

    private fun getTimeOffset(timeMls: Long): String {
        return (Date().time - timeMls).toString()
    }

    private fun convertBodyToJson(body: String?): JSONObject? {
        return if (body == null) {
            null
        } else try {
            JSONObject(body)
        } catch (e: JSONException) {
            null
        }
    }

    private fun parseResponse(response: NetworkResponse?): MindboxResponse {
        return when {
            response == null -> MindboxResponse.Error(-1, byteArrayOf()) // response can be null
            response.statusCode < 300 -> {
                MindboxResponse.SuccessResponse(response.data)
            }
            response.statusCode in 400..499 -> {
                // separate condition for removing from the queue
                MindboxResponse.BadRequest(response.statusCode)
            }
            else -> {
                MindboxResponse.Error(response.statusCode, response.data)
            }
        }
    }
}