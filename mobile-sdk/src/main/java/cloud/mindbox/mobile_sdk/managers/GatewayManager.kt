package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.Configuration
import cloud.mindbox.mobile_sdk.InitializeMindboxException
import cloud.mindbox.mobile_sdk.models.FullInitData
import cloud.mindbox.mobile_sdk.models.MindboxRequest
import cloud.mindbox.mobile_sdk.models.MindboxResponse
import cloud.mindbox.mobile_sdk.models.PartialInitData
import cloud.mindbox.mobile_sdk.network.RestApi
import cloud.mindbox.mobile_sdk.network.ServiceGenerator
import cloud.mindbox.mobile_sdk.network.ServiceGeneratorOld
import com.android.volley.Request
import com.google.gson.Gson
import org.json.JSONObject
import java.util.*

object GatewayManager {

    private val gson = Gson()

    private const val BASE_URL_PLACEHOLDER = "https://%1$1s/%2$1s"
    private const val URL_PLACEHOLDER = "%1$1s?endpointId=%2$1s&operation=%3$1s&deviceUUID=%4$1s"

    internal fun buildUrl(
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

    private var mindboxApi: RestApi? = null

    private const val OPERATION_APP_INSTALLED = "MobileApplicationInstalled"
    private const val OPERATION_APP_UPDATE = "MobileApplicationInfoUpdated"

    fun initClient(domain: String, packageName: String, versionName: String, versionCode: String) {
        mindboxApi = ServiceGeneratorOld.initRetrofit(domain, packageName, versionName, versionCode)
            .create(RestApi::class.java)
    }

    fun sendFirstInitialization(
        context: Context,
        configuration: Configuration,
        data: FullInitData?
    ) {
        if (mindboxApi == null) throw InitializeMindboxException("Network client is not initialized!")

        val dataObject = JSONObject(gson.toJson(data))

        val request = MindboxRequest(
            Request.Method.POST,
            buildUrl("api.mindbox.ru", "test", OPERATION_APP_INSTALLED, configuration),
            configuration,
            dataObject,
            { response ->
                parseResponse()
            },
            {

            }
        )

        ServiceGenerator.getInstance(context).addToRequestQueue(request)

    }

    fun sendSecondInitialization(
        context: Context,
        configuration: Configuration,
        data: PartialInitData
    ) {
        if (mindboxApi == null) throw InitializeMindboxException("Network client is not initialized!")

        val dataObject = JSONObject(gson.toJson(data))

        val request = MindboxRequest(
            Request.Method.POST,
            buildUrl("api.mindbox.ru", "test", OPERATION_APP_UPDATE, configuration),
            configuration,
            dataObject,
            { response ->
                parseResponse()
            },
            {

            }
        )

        ServiceGenerator.getInstance(context).addToRequestQueue(request)
    }

    private fun getTimeOffset(date: Date): Long {
        return Date().time - date.time
    }

    private fun parseResponse(): MindboxResponse? {
        return null
//        return if (response.isSuccessful && response.code() < 300) {
//            MindboxResponse.SuccessResponse(response.code(), response.body())
//        } else if (response.code() in 400..499) {
//            // separate condition for removing from the queue
//            MindboxResponse.Error(response.code(), response.message(), response.errorBody())
//        } else {
//            MindboxResponse.Error(response.code(), response.message(), response.errorBody())
//        }
    }
}