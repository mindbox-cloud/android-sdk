package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors

import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

internal interface InAppInteractor {

    suspend fun listenToTargetingEvents()

    fun isInAppShown(): Boolean
    fun setInAppShown()
    suspend fun processEventAndConfig(): Flow<InAppType>

    fun saveShownInApp(id: String)

    fun sendInAppClicked(inAppId: String)

    suspend fun fetchMobileConfig()
}