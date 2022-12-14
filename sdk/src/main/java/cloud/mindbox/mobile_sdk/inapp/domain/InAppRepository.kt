package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppConfig
import cloud.mindbox.mobile_sdk.inapp.domain.models.SegmentationCheckInApp
import cloud.mindbox.mobile_sdk.models.InAppEventType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow

internal interface InAppRepository {

    fun getShownInApps(): HashSet<String>

    suspend fun fetchInAppConfig()

    suspend fun fetchSegmentations(config: InAppConfig): SegmentationCheckInApp

    fun listenInAppConfig(): Flow<InAppConfig?>

    fun listenInAppEvents(): Flow<InAppEventType>

    fun saveShownInApp(id: String)

    fun sendInAppShown(inAppId: String)

    fun sendInAppClicked(inAppId: String)

    fun sendInAppTargetingHit(inAppId: String)
}