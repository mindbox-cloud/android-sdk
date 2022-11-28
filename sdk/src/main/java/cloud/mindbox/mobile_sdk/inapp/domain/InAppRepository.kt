package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.models.InApp
import cloud.mindbox.mobile_sdk.models.InAppConfig
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.SegmentationCheckInApp
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import kotlinx.coroutines.flow.Flow

internal interface InAppRepository {

    val shownInApps: HashSet<String>

    suspend fun fetchInAppConfig(configuration: MindboxConfiguration)

    suspend fun fetchSegmentations(
        configuration: MindboxConfiguration,
        config: InAppConfig,
    ): SegmentationCheckInApp

    fun deserializeConfigToConfigDto(inAppConfig: String): InAppConfigResponse?

    fun listenInAppConfig(): Flow<InAppConfig?>

    fun listenInAppEvents(): Flow<InAppEventType>

    fun saveShownInApp(id: String)

    fun sendInAppShown(inAppId: String)

    fun sendInAppClicked(inAppId: String)
}