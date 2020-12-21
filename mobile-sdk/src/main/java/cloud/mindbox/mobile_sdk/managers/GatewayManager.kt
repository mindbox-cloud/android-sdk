package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.models.FullInitData
import cloud.mindbox.mobile_sdk.models.PartialInitData
import cloud.mindbox.mobile_sdk.network.RestApi
import cloud.mindbox.mobile_sdk.network.ServiceGenerator

object GatewayManager {

    private val mindboxApi = ServiceGenerator.initRetrofit().create(RestApi::class.java)

    private const val OPERATION_APP_INSTALLED = "MobileApplicationInstalled"
    private const val OPERATION_APP_UPDATE = "MobileApplicationInfoUpdated"

    suspend fun sendFirstInitialization(endpointId: String, deviceId: String, data: FullInitData) {
        mindboxApi.firstInitSdk(
            endpointId = endpointId,
            operation = OPERATION_APP_INSTALLED,
            deviceId = deviceId,
            data = data
        )
    }

    suspend fun sendSecondInitialization(
        endpointId: String,
        deviceId: String,
        data: PartialInitData
    ) {

    }
}