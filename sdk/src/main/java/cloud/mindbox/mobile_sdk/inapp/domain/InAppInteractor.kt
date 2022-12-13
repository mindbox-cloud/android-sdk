package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import kotlinx.coroutines.flow.Flow

internal interface InAppInteractor {

    fun processEventAndConfig(): Flow<InAppType>

    fun saveShownInApp(id: String)

    fun sendInAppShown(inAppId: String)

    fun sendInAppClicked(inAppId: String)

    suspend fun fetchInAppConfig()
}