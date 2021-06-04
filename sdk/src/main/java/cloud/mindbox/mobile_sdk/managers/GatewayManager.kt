package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import android.util.Log
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.models.*
import cloud.mindbox.mobile_sdk.models.operation.response.OperationResponseBase
import cloud.mindbox.mobile_sdk.network.MindboxServiceGenerator
import cloud.mindbox.mobile_sdk.toUrlQueryString
import com.android.volley.DefaultRetryPolicy
import com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONException
import org.json.JSONObject
import java.util.*

internal object GatewayManager {

    private const val TIMEOUT_DELAY = 60000
    private const val MAX_RETRIES = 0

    private fun buildEventUrl(
        configuration: Configuration,
        deviceUuid: String,
        event: Event
    ): String {

        val urlQueries: HashMap<String, String> = hashMapOf(
            UrlQuery.DEVICE_UUID.value to deviceUuid,
        )

        when (event.eventType) {
            is EventType.AppInstalled,
            is EventType.AppInfoUpdated,
            is EventType.PushClicked,
            is EventType.AsyncOperation -> {
                urlQueries[UrlQuery.ENDPOINT_ID.value] = configuration.endpointId
                urlQueries[UrlQuery.OPERATION.value] = event.eventType.operation
                urlQueries[UrlQuery.TRANSACTION_ID.value] = event.transactionId
                urlQueries[UrlQuery.DATE_TIME_OFFSET.value] = getTimeOffset(event.enqueueTimestamp)
            }
            is EventType.PushDelivered -> {
                urlQueries[UrlQuery.ENDPOINT_ID.value] = configuration.endpointId
                urlQueries[UrlQuery.UNIQ_KEY.value] =
                    event.additionalFields?.get(EventParameters.UNIQ_KEY.fieldName) ?: ""
                urlQueries[UrlQuery.TRANSACTION_ID.value] = event.transactionId
                urlQueries[UrlQuery.DATE_TIME_OFFSET.value] = getTimeOffset(event.enqueueTimestamp)
            }
            is EventType.TrackVisit -> {
                urlQueries[UrlQuery.TRANSACTION_ID.value] = event.transactionId
                urlQueries[UrlQuery.DATE_TIME_OFFSET.value] = getTimeOffset(event.enqueueTimestamp)
            }
            is EventType.SyncOperation -> {
                urlQueries[UrlQuery.ENDPOINT_ID.value] = configuration.endpointId
                urlQueries[UrlQuery.OPERATION.value] = event.eventType.operation
            }
        }

        return "https://${configuration.domain}${event.eventType.endpoint}${urlQueries.toUrlQueryString()}"
    }

    fun sendAsyncEvent(
        context: Context,
        configuration: Configuration,
        deviceUuid: String,
        event: Event,
        isSuccessListener: (Boolean) -> Unit
    ) = sendEvent<Any>(
        context = context,
        configuration = configuration,
        deviceUuid = deviceUuid,
        event = event,
        { isSuccessListener.invoke(true) },
        { isSuccessListener.invoke(false) }
    )

    fun <T> sendSyncEvent(
        context: Context,
        configuration: Configuration,
        deviceUuid: String,
        event: Event,
        onSuccess: (T) -> Unit,
        onError: (MindboxError) -> Unit
    ) = sendEvent<T>(
        context = context,
        configuration = configuration,
        deviceUuid = deviceUuid,
        event = event,
        onSuccess = {},
        onError = onError
    )

    private fun <T> sendEvent(
        context: Context,
        configuration: Configuration,
        deviceUuid: String,
        event: Event,
        onSuccess: (T?) -> Unit,
        onError: (MindboxError) -> Unit
    ) {
        try {

            val requestType: Int = getRequestType(event.eventType)
            val url: String = buildEventUrl(configuration, deviceUuid, event)
            val jsonRequest: JSONObject? = convertBodyToJson(event.body)

            val request = MindboxRequest(requestType, url, configuration, jsonRequest,
                {
                    MindboxLogger.d(this, "Event from background successful sent")
                    val responseString = it.toString()
                    /*if (responseString is T){
                        onSuccess.invoke(it.toString())
                    } else {
                        val response =
                            Gson().fromJson<T>(it.toString(), object : TypeToken<T>() {}.type)*/
                        onSuccess.invoke(null)
                   // }
                }, { volleyError ->
                    try {
                        val error = volleyError.networkResponse
                        when{
                            error == null -> MindboxError.Unknown(status = "Unknown")
                            error.statusCode < 300 -> {
                                Log.d("_____", "")
                                //MindboxResponse.SuccessResponse(response.data)
                            }
                            error.statusCode in 400..499 -> {
                                Log.d("_____", "")
                                Gson().fromJson<TestError>(String(error.data), object : TypeToken<TestError>() {}.type)
                                //MindboxResponse.BadRequest(error.statusCode)
                            }
                            error.statusCode in 500..599 -> {
                                Log.d("_____", "")
                                //MindboxResponse.BadRequest(error.statusCode)
                            }
                            else -> {
                                Log.d("_____", "")
                                //MindboxResponse.Error(error.statusCode, response.data)
                            }
                        }

                       /* when (val result = parseResponse(volleyError.networkResponse)) {
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
                        }*/
                    } catch (e: Exception) {
                        MindboxLogger.e(this, "Parsing server response was failure", e)
                        //isSuccess.invoke(false)
                    }
                }
            ).apply {
                setShouldCache(false)
                retryPolicy = DefaultRetryPolicy(TIMEOUT_DELAY, MAX_RETRIES, DEFAULT_BACKOFF_MULT)
            }

            MindboxServiceGenerator.getInstance(context)?.addToRequestQueue(request)
        } catch (e: Exception) {
            MindboxLogger.e(this, "Sending event was failure with exception", e)
            //isSuccess.invoke(false)
        }
    }

    private fun getRequestType(eventType: EventType): Int = when (eventType) {
        is EventType.AppInstalled,
        is EventType.AppInfoUpdated,
        is EventType.PushClicked,
        is EventType.TrackVisit,
        is EventType.AsyncOperation,
        is EventType.SyncOperation -> Request.Method.POST
        is EventType.PushDelivered -> Request.Method.GET
    }

    private fun getTimeOffset(
        timeMls: Long
    ): String = (System.currentTimeMillis() - timeMls).toString()

    private fun convertBodyToJson(body: String?): JSONObject? {
        return if (body == null) {
            null
        } else try {
            JSONObject(body)
        } catch (e: JSONException) {
            null
        }
    }

}
