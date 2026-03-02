package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors

import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.models.Milliseconds
import kotlinx.coroutines.flow.Flow

internal interface InAppInteractor {

    suspend fun listenToTargetingEvents()

    fun setInAppShown(inAppId: String)

    suspend fun processEventAndConfig(): Flow<Pair<InApp, Milliseconds>>

    fun saveShownInApp(
        id: String,
        timeStamp: Long,
        timeToDisplay: String,
        tags: Map<String, String>?
    )

    fun sendInAppClicked(inAppId: String)

    suspend fun fetchMobileConfig()

    fun resetInAppConfigAndEvents()

    fun isTimeDelayInapp(inAppId: String): Boolean

    fun saveInAppDismissTime()

    fun areShowAndFrequencyLimitsAllowed(inApp: InApp): Boolean
}
