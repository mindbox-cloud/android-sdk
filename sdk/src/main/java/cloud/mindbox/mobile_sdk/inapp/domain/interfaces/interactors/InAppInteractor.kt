package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors

import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import kotlinx.coroutines.flow.Flow

internal interface InAppInteractor {

    fun isInAppShown(): Boolean
    fun setInAppShown()
    suspend fun processEventAndConfig(): Flow<InAppType>

    fun saveShownInApp(id: String)

    fun sendInAppShown(inAppId: String)

    fun sendInAppClicked(inAppId: String)

    suspend fun fetchMobileConfig()
}