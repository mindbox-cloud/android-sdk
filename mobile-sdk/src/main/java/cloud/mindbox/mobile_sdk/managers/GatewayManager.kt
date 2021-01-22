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
import com.android.volley.toolbox.JsonObjectRequest
import org.json.JSONObject
//import retrofit2.Response
import java.util.*

object GatewayManager {

    private const val BASE_URL_PLACEHOLDER = "https://%1$1s/%2$1s"
    private const val URL_PLACEHOLDER = "%1$1s?endpointId=%2$1s&operation=%3$1s&deviceUUID=%4$1s"

    internal fun buildUrl(domain: String, endpoint: String, operationType: String, configuration: Configuration): String {
        val domain = String.format(BASE_URL_PLACEHOLDER, domain, endpoint)
        return String.format(URL_PLACEHOLDER, domain, configuration.endpoint, operationType, configuration.deviceId)
    }

    private var mindboxApi: RestApi? = null

    private const val OPERATION_APP_INSTALLED = "MobileApplicationInstalled"
    private const val OPERATION_APP_UPDATE = "MobileApplicationInfoUpdated"

    fun initClient(domain: String, packageName: String, versionName: String, versionCode: String) {
        mindboxApi = ServiceGeneratorOld.initRetrofit(domain, packageName, versionName, versionCode)
            .create(RestApi::class.java)
    }

    suspend fun sendFirstInitialization(
        endpointId: String,
        deviceId: String,
        data: FullInitData
    ): MindboxResponse? {
        if (mindboxApi == null) throw InitializeMindboxException("Network client is not initialized!")

        val result = mindboxApi!!.firstInitSdk(
            endpointId = endpointId,
            operation = OPERATION_APP_INSTALLED,
            deviceId = deviceId,
            data = data
        )

        return parseResponse()
    }

    suspend fun sendSecondInitialization(
        endpointId: String,
        deviceId: String,
        data: PartialInitData
    ): MindboxResponse? {
        if (mindboxApi == null) throw InitializeMindboxException("Network client is not initialized!")

        val result = mindboxApi!!.secondInitSdk(
            endpointId = endpointId,
            operation = OPERATION_APP_UPDATE,
            deviceId = deviceId,
            data = data
        )

        return parseResponse()
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

    fun testRequest(context: Context, configuration: Configuration) {

        val request = MindboxRequest(
            Request.Method.POST, buildUrl("api.mindbox.ru", "test", OPERATION_APP_INSTALLED, configuration), configuration, OPERATION_APP_INSTALLED, JSONObject(),
            { response ->

            }, {

            }
        )

        ServiceGenerator.getInstance(context).addToRequestQueue(request)

// Add the request to the RequestQueue.
    }
}