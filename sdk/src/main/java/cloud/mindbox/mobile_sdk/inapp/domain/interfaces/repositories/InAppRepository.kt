package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories

import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.models.InAppEventType
import kotlinx.coroutines.flow.Flow

internal interface InAppRepository {
    fun saveCurrentSessionInApps(inApps: List<InApp>)

    fun getShownInApps(): Map<String, Long>

    fun getCurrentSessionInApps(): List<InApp>

    fun getTargetedInApps(): Map<String, MutableSet<Int>>

    fun saveTargetedInAppWithEvent(inAppId: String, eventHashcode: Int)

    fun saveUnShownOperationalInApp(operation: String, inApp: InApp)

    fun getUnShownOperationalInAppsByOperation(operation: String): List<InApp>

    fun saveOperationalInApp(operation: String, inApp: InApp)

    fun getOperationalInAppsByOperation(operation: String): List<InApp>

    fun listenInAppEvents(): Flow<InAppEventType>

    fun saveShownInApp(id: String, timeStamp: Long)

    fun sendInAppShown(inAppId: String)

    fun sendInAppClicked(inAppId: String)

    fun sendUserTargeted(inAppId: String)

    fun setInAppShown(inAppId: String)

    fun isInAppShown(inAppId: String): Boolean

    fun clearInAppEvents()

    fun isTimeDelayInapp(inAppId: String): Boolean
}
