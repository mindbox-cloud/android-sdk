package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import android.util.Log
import cloud.mindbox.mobile_sdk.Configuration
import cloud.mindbox.mobile_sdk.InitializeMindboxException
import cloud.mindbox.mobile_sdk.Logger
import cloud.mindbox.mobile_sdk.models.*
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.network.ServiceGenerator
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.google.gson.Gson
import org.json.JSONObject
import java.util.*

internal object GatewayManager {

    private val gson = Gson()

    private const val BASE_URL_PLACEHOLDER = "https://%1$1s/%2$1s"
    private const val URL_PLACEHOLDER = "%1$1s?endpointId=%2$1s&operation=%3$1s&deviceUUID=%4$1s"
    private const val URL_EVENT_PLACEHOLDER = "%1$1s?endpointId=%2$1s&operation=%3$1s&deviceUUID=%4$1s&transactionId=%5$1s&dateTimeOffset=%6$1s"

    private fun buildUrl(
        domain: String,
        endpoint: String,
        operationType: String,
        configuration: Configuration
    ): String {
        val url = String.format(BASE_URL_PLACEHOLDER, domain, endpoint)
        return String.format(
            URL_PLACEHOLDER,
            url,
            configuration.endpoint,
            operationType,
            configuration.deviceId
        )
    }

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
            configuration.deviceId,
            transactionId,
            dateTimeOffset
        )
    }

    private const val OPERATION_APP_INSTALLED = "MobileApplicationInstalled"
    private const val OPERATION_APP_UPDATE = "MobileApplicationInfoUpdated"

    fun sendFirstInitialization(
        context: Context,
        configuration: Configuration,
        data: FullInitData?,
        onResult: (MindboxResponse) -> Unit
    ) {
        val dataObject = JSONObject(gson.toJson(data))

        val request = MindboxRequest(
            Request.Method.POST,
            buildUrl(
                configuration.domain,
                configuration.endpoint,
                OPERATION_APP_INSTALLED,
                configuration
            ),
            configuration,
            dataObject,
            { response ->
                onResult.invoke(MindboxResponse.SuccessResponse(response))
            }, {
                onResult.invoke(parseResponse(it.networkResponse))
            }
        )

        ServiceGenerator.getInstance(context).addToRequestQueue(request)
    }

    fun sendSecondInitialization(
        context: Context,
        configuration: Configuration,
        data: PartialInitData,
        onResult: (MindboxResponse) -> Unit
    ) {
        val dataObject = JSONObject(gson.toJson(data))

        val request = MindboxRequest(
            Request.Method.POST,
            buildUrl(
                configuration.domain,
                configuration.endpoint,
                OPERATION_APP_UPDATE,
                configuration
            ),
            configuration,
            dataObject,
            { response ->
                onResult.invoke(MindboxResponse.SuccessResponse(response))
            }, {
                onResult.invoke(parseResponse(it.networkResponse))
            }
        )

        ServiceGenerator.getInstance(context).addToRequestQueue(request)
    }

    fun sendEvent(context: Context, event: Event) {
        val dataObject = JSONObject(event.data)
        val configuration = DbManager.getConfigurations()

        if (configuration == null) {
            Logger.e(this, "Configuration was not initialized", InitializeMindboxException("Configuration was not initialized"))
            return
        }

        val request = MindboxRequest(
            Request.Method.POST,
            buildEventUrl(
                configuration,
                OPERATION_APP_UPDATE,
                event.transactionId,
                getTimeOffset(event.enqueueTimestamp)
            ),
            configuration,
            dataObject,
            { response ->
                //todo
//                onResult.invoke(MindboxResponse.SuccessResponse(response))
            }, {
//                onResult.invoke(parseResponse(it.networkResponse))
            }
        )

        ServiceGenerator.getInstance(context).addToRequestQueue(request)
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
                MindboxResponse.Error(response.statusCode, response.data)
            }
            else -> {
                MindboxResponse.Error(response.statusCode, response.data)
            }
        }
    }
}