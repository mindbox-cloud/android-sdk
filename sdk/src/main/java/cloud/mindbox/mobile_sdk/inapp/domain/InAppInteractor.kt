package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.models.CustomerSegmentationInApp
import cloud.mindbox.mobile_sdk.models.InApp
import cloud.mindbox.mobile_sdk.models.InAppConfig
import kotlinx.coroutines.flow.Flow

internal interface InAppInteractor {

    fun processEventAndConfig(
        configuration: MindboxConfiguration,
    ): Flow<InAppType>

    suspend fun chooseInAppToShow(
        config: InAppConfig,
        configuration: MindboxConfiguration,
    ): InApp?

    fun saveShownInApp(id: String)

    fun sendInAppShown(inAppId: String)

    fun sendInAppClicked(inAppId: String)

    fun validateInAppVersion(inApp: InApp): Boolean

    fun validateSegmentation(
        inApp: InApp,
        customerSegmentationInApp: CustomerSegmentationInApp,
    ): Boolean

    fun validateInAppNotShown(inApp: InApp): Boolean

    fun getConfigWithTargeting(config: InAppConfig): InAppConfig

    fun prefilterConfig(config: InAppConfig): InAppConfig

    fun validateInAppTargeting(inApp: InApp): Boolean

    suspend fun fetchInAppConfig(configuration: MindboxConfiguration)
}