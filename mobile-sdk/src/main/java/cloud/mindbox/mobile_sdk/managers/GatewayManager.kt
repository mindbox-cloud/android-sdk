package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.models.InitData
import cloud.mindbox.mobile_sdk.models.PartialInitData
import cloud.mindbox.mobile_sdk.network.ServiceGenerator

object GatewayManager {

    private const val OPERATION_APP_INSTALLED = "MobileApplicationInstalled"
    private const val OPERATION_APP_UPDATE = "MobileApplicationInfoUpdated"

    fun sendFirstInitialization(endpointId: String, deviceId: String, data: InitData) {
        ServiceGenerator.mindboxApi.firstInitSdk(
            endpointId = endpointId,
            operation = OPERATION_APP_INSTALLED,
            deviceId = deviceId,
            data = data
        )
    }

    fun sendSecondInitialization(endpointId: String, deviceId: String, data: PartialInitData) {

    }
}