package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.InitializeMindboxException
import cloud.mindbox.mobile_sdk.models.FullInitData
import cloud.mindbox.mobile_sdk.models.InitResponse
import cloud.mindbox.mobile_sdk.models.MindboxResponse
import cloud.mindbox.mobile_sdk.models.PartialInitData
import cloud.mindbox.mobile_sdk.network.RestApi
import cloud.mindbox.mobile_sdk.network.ServiceGenerator
import retrofit2.Response
import java.util.*

object GatewayManager {

    private var mindboxApi: RestApi? = null

    private const val OPERATION_APP_INSTALLED = "MobileApplicationInstalled"
    private const val OPERATION_APP_UPDATE = "MobileApplicationInfoUpdated"

    fun initClient(domain: String, packageName: String, versionName: String, versionCode: String) {
        mindboxApi = ServiceGenerator.initRetrofit(domain, packageName, versionName, versionCode)
            .create(RestApi::class.java)
    }

    suspend fun sendFirstInitialization(
        endpointId: String,
        deviceId: String,
        data: FullInitData
    ): MindboxResponse {
        if (mindboxApi == null) throw InitializeMindboxException("Network client is not initialized!")

        val result = mindboxApi!!.firstInitSdk(
            endpointId = endpointId,
            operation = OPERATION_APP_INSTALLED,
            deviceId = deviceId,
            data = data
        )

        return parseResponse(result)
    }

    suspend fun sendSecondInitialization(
        endpointId: String,
        deviceId: String,
        data: PartialInitData
    ): MindboxResponse {
        if (mindboxApi == null) throw InitializeMindboxException("Network client is not initialized!")

        val result = mindboxApi!!.secondInitSdk(
            endpointId = endpointId,
            operation = OPERATION_APP_UPDATE,
            deviceId = deviceId,
            data = data
        )

        return parseResponse(result)
    }

    private fun getTimeOffset(date: Date): Long {
        return Date().time - date.time
    }

    private fun parseResponse(response: Response<InitResponse>): MindboxResponse {
        return if (response.isSuccessful && response.code() < 300) {
            MindboxResponse.SuccessResponse(response.code(), response.body())
        } else {
            MindboxResponse.Error(response.code(), response.message(), response.errorBody())
        }
    }
}