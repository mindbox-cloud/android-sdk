package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors

import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import kotlinx.coroutines.flow.Flow

internal interface InAppInteractor {

    suspend fun listenToTargetingEvents()

    fun isInAppShown(inAppId: String): Boolean

    fun setInAppShown(inAppId: String)

    suspend fun processEventAndConfig(): Flow<InAppType>

    fun saveShownInApp(id: String, timeStamp: Long)

    fun sendInAppClicked(inAppId: String)

    suspend fun fetchMobileConfig()

    fun resetInAppConfigAndEvents()

    fun isTimeDelayInapp(inAppId: String): Boolean

    fun saveLastDismissInAppTime()
}
