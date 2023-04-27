package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories

import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.models.InAppEventType
import kotlinx.coroutines.flow.Flow

internal interface InAppRepository {

    fun getInAppContentTimeout(): Long
    fun saveOperationalInApp(operation: String, inApp: InApp)

    fun getOperationalInAppsByOperation(operation: String): List<InApp>

    fun getShownInApps(): Set<String>

    fun listenInAppEvents(): Flow<InAppEventType>

    fun saveShownInApp(id: String)

    fun sendInAppShown(inAppId: String)

    fun sendInAppClicked(inAppId: String)

    fun sendUserTargeted(inAppId: String)

    fun setInAppShown()

    fun isInAppShown(): Boolean
}