package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppConfig
import cloud.mindbox.mobile_sdk.models.InAppStub

internal class InAppConfigStub {
    companion object {
        fun getConfig(): InAppConfig =
            InAppConfig(inApps = listOf(InAppStub.getInApp()))

        fun getConfigDto(): InAppConfigResponse =
            InAppConfigResponse(inApps = listOf(InAppStub.getInAppDto()))

    }
}