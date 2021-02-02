package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.Configuration
import cloud.mindbox.mobile_sdk.InitializeMindboxException
import cloud.mindbox.mobile_sdk.Logger
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.MindboxRequest
import cloud.mindbox.mobile_sdk.models.MindboxResponse
import cloud.mindbox.mobile_sdk.network.ServiceGenerator
import com.android.volley.NetworkResponse
import com.android.volley.Request
import org.json.JSONObject
import java.util.*

internal object GatewayManager {


    private const val BASE_URL_PLACEHOLDER = "https://%1$1s/v3/operations/async"
    private const val URL_EVENT_PLACEHOLDER =
        "%1$1s?endpointId=%2$1s&operation=%3$1s&deviceUUID=%4$1s&transactionId=%5$1s&dateTimeOffset=%6$1s"

    private fun buildEventUrl(
        configuration: Configuration,
        operationType: String,
        transactionId: String,
        dateTimeOffset: Long
    ): String {
        val url = String.format(BASE_URL_PLACEHOLDER, configuration.domain, configuration.endpoint)
        return String.format(
            URL_EVENT_PLACEHOLDER,
            url,
            configuration.endpoint,
            operationType,
            configuration.deviceUuid,
            transactionId,
            dateTimeOffset
        )
    }

    fun sendEvent(context: Context, event: Event, isSuccess: (Boolean) -> Unit) {
        try {
            val dataObject = JSONObject(event.body)
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

            val url = buildEventUrl(
                configuration,
                event.eventType.type,
                event.transactionId,
                getTimeOffset(event.enqueueTimestamp)
            )

            val request = MindboxRequest(Request.Method.POST, url, configuration, dataObject,
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
                            else -> {
                                Logger.d(
                                    this,
                                    "Sending event from background was failure as unknown error"
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

    private fun getTimeOffset(timeMls: Long): Long {
        return Date().time - timeMls
    }

    private fun parseResponse(response: NetworkResponse?): MindboxResponse {
        return when {
            response == null -> MindboxResponse.Error(0, byteArrayOf())
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