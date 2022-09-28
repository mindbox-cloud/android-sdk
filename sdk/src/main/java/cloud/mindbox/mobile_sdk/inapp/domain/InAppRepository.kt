package cloud.mindbox.mobile_sdk.inapp.domain

import android.content.Context
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.models.InAppConfig
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.SegmentationCheckInApp
import kotlinx.coroutines.flow.Flow

internal interface InAppRepository {

    fun fetchInAppConfig(context: Context, configuration: MindboxConfiguration)

    suspend fun fetchSegmentations(
        context: Context,
        configuration: MindboxConfiguration,
        config: InAppConfig,
    ): SegmentationCheckInApp

    fun listenInAppConfig(): Flow<InAppConfig>

    fun listenInAppEvents(): Flow<InAppEventType>
}