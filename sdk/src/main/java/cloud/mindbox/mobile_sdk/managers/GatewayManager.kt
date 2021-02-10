package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.Configuration
import cloud.mindbox.mobile_sdk.InitializeMindboxException
import cloud.mindbox.mobile_sdk.Logger
import cloud.mindbox.mobile_sdk.models.*
import cloud.mindbox.mobile_sdk.network.ServiceGenerator
import cloud.mindbox.mobile_sdk.toUrlQueryString
import com.android.volley.NetworkResponse
import com.android.volley.Request
import org.json.JSONObject
import java.util.*

internal object GatewayManager {


    private const val BASE_URL_PLACEHOLDER = "https://%1$1s"
    private const val URL_APP_EVENT_PLACEHOLDER =
        "%1$1s/v3/operations/async?" +
                "endpointId=%2$1s&" +
                "operation=%3$1s&" +
                "deviceUUID=%4$1s&" +
                "transactionId=%5$1s&" +
                "dateTimeOffset=%6$1s"
    private const val URL_PUSH_EVENT_PLACEHOLDER =
        "/mobile-push/delivered?" +
                "uniqKey=& " +
                "endpointId=&" +
                "deviceUUID="

    private fun buildEventUrl(
        configuration: Configuration,
        event: Event
    ): String {
//        val urlQueries: HashMap<String, String> = when (event.eventType) {
//            EventType.APP_INFO_UPDATED,
//            EventType.APP_INSTALLED -> {
//                hashMapOf(
//                    UrlQuery.ENDPOINT_ID.value to configuration.endpointId,
//                    UrlQuery.OPERATION.value to event.eventType.type,
//                    UrlQuery.DEVICE_UUID.value to configuration.deviceUuid,
//                    UrlQuery.TRANSACTION_ID.value to event.transactionId,
//                    UrlQuery.DATE_TIME_OFFSET.value to getTimeOffset(event.enqueueTimestamp)
//                )
//            }
//            EventType.PUSH_DELIVERED -> {
//                hashMapOf(
//                    UrlQuery.UNIQ_KEY.value  to (event.uniqKey ?: ""),
//                    UrlQuery.ENDPOINT_ID.value to configuration.endpointId,
//                    UrlQuery.DEVICE_UUID.value to configuration.deviceUuid,
//                    UrlQuery.TRANSACTION_ID.value to event.transactionId,
//                    UrlQuery.DATE_TIME_OFFSET.value to getTimeOffset(event.enqueueTimestamp)
//                )
//            }
//        }

        val urlQueries: HashMap<String, String> = hashMapOf(
            UrlQuery.ENDPOINT_ID.value to configuration.endpointId,
            UrlQuery.DEVICE_UUID.value to configuration.deviceUuid,
            UrlQuery.TRANSACTION_ID.value to event.transactionId,
            UrlQuery.DATE_TIME_OFFSET.value to getTimeOffset(event.enqueueTimestamp)
        )

        when (event.eventType) {
            EventType.APP_INFO_UPDATED,
            EventType.APP_INSTALLED -> {
                urlQueries[UrlQuery.OPERATION.value] = event.eventType.operation
            }
            EventType.PUSH_DELIVERED -> {
                urlQueries[UrlQuery.UNIQ_KEY.value] = event.uniqKey ?: ""
            }
        }

        urlQueries.toUrlQueryString()

        val url = "https://${configuration.domain}${event.eventType.endpoint}${urlQueries.toUrlQueryString()}"

//        val baseUrl = String.format(BASE_URL_PLACEHOLDER, configuration.domain)
//
//        return String.format(
//            URL_EVENT_PLACEHOLDER,
//            url,
//            configuration.endpointId,
//            event.eventType.operation,
//            configuration.deviceUuid,
//            event.transactionId,
//            getTimeOffset(event.enqueueTimestamp)
//        )

        return url
    }

    fun sendEvent(context: Context, event: Event, isSuccess: (Boolean) -> Unit) {
        try {
            val configuration = DbManager.getConfigurations()

            if (configuration == null) {
                Logger.e(
                    this,
                    "Configuration was not initialized",
                    InitializeMindboxException("Configuration was not initialized")
                )
                isSuccess.invoke(false)
                return
            }

            val requestType: Int = getRequestType(event.eventType)
            val url: String = buildEventUrl(configuration, event)
            val jsonRequest: JSONObject? = convertBodyToJson(event.body)

            val request = MindboxRequest(requestType, url, configuration, jsonRequest,
                {
                    Logger.d(this, "Event from background successful sended")
                    isSuccess.invoke(true)
                }, {
                    try {
                        when (val result = parseResponse(it.networkResponse)) {
                            is MindboxResponse.SuccessResponse<*>,
                            is MindboxResponse.BadRequest -> {
                                Logger.d(this, "Event from background successful sended")
                                isSuccess.invoke(true)
                            }
                            is MindboxResponse.Error -> {
                                Logger.d(
                                    this,
                                    "Sending event from background was failure with code ${result.status}"
                                )
                                isSuccess.invoke(false)
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e(this, "Parsing server response was failure", e)
                        isSuccess.invoke(false)
                    }
                }
            )

            ServiceGenerator.getInstance(context).addToRequestQueue(request)
        } catch (e: Exception) {
            Logger.e(this, "Sending event was failure with exception", e)
            isSuccess.invoke(false)
        }
    }

    private fun getRequestType(eventType: EventType): Int {
        return when (eventType) {
            EventType.APP_INSTALLED,
            EventType.APP_INFO_UPDATED -> Request.Method.POST
            EventType.PUSH_DELIVERED -> Request.Method.GET
        }
    }

    private fun getTimeOffset(timeMls: Long): String {
        return (Date().time - timeMls).toString()
    }

    private fun convertBodyToJson(body: String?): JSONObject? {
        return if (body == null) {
            null
        } else {
            JSONObject(body)
        }
    }

    private fun parseResponse(response: NetworkResponse?): MindboxResponse {
        return when {
            response == null -> MindboxResponse.Error(-1, byteArrayOf())
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