package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.inapp.domain.models.*
import cloud.mindbox.mobile_sdk.inapp.domain.models.CustomerSegmentationInApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppConfig
import cloud.mindbox.mobile_sdk.inapp.domain.models.TreeTargeting
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

    fun validateSegmentation(
        inApp: InApp,
        customerSegmentationInApp: CustomerSegmentationInApp,
    ): Boolean

    fun validateInAppNotShown(inApp: InApp): Boolean

    fun getConfigWithTargeting(config: InAppConfig): InAppConfig

    fun prefilterConfig(config: InAppConfig): InAppConfig

    fun validateInAppTargeting(targeting: TreeTargeting?): Boolean

    suspend fun fetchInAppConfig(configuration: MindboxConfiguration)
}