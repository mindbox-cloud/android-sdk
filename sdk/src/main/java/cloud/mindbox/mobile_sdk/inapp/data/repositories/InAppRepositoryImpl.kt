package cloud.mindbox.mobile_sdk.inapp.data.repositories

import android.content.Context
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import kotlinx.coroutines.flow.Flow

internal class InAppRepositoryImpl(
    private val context: Context,
    private val sessionStorageManager: SessionStorageManager,
    private val inAppSerializationManager: InAppSerializationManager,
) :
    InAppRepository {

    override fun saveOperationalInApp(operation: String, inApp: InApp) {
        sessionStorageManager.operationalInApps[operation] =
            sessionStorageManager.operationalInApps.getOrElse(operation) {
                mutableListOf()
            }.apply {
                add(inApp)
            }
    }

    override fun getOperationalInAppsByOperation(operation: String): List<InApp> {
        return sessionStorageManager.operationalInApps[operation] ?: emptyList()
    }

    override fun getShownInApps(): MutableSet<String> {
        return inAppSerializationManager.deserializeToShownInApps(MindboxPreferences.shownInAppIds)
    }

    override fun listenInAppEvents(): Flow<InAppEventType> {
        return MindboxEventManager.eventFlow
    }

    override fun saveShownInApp(id: String) {
        inAppSerializationManager.serializeToShownInAppsString(getShownInApps().apply { add(id) })
            .apply {
                if (isNotBlank()) {
                    MindboxPreferences.shownInAppIds = this
                }
            }
    }


    override fun sendInAppShown(inAppId: String) {
        inAppSerializationManager.serializeToInAppHandledString(inAppId).apply {
            if (isNotBlank()) {
                MindboxEventManager.inAppShown(
                    context,
                    this
                )
            }
        }
    }

    override fun sendInAppClicked(inAppId: String) {
        inAppSerializationManager.serializeToInAppHandledString(inAppId).apply {
            if (isNotBlank()) {
                MindboxEventManager.inAppClicked(
                    context,
                    this
                )
            }
        }
    }

    override fun sendUserTargeted(inAppId: String) {
        inAppSerializationManager.serializeToInAppHandledString(inAppId).apply {
            if (isNotBlank()) {
                MindboxEventManager.sendUserTargeted(
                    context,
                    this
                )
            }
        }
    }

    override fun setInAppShown() {
        sessionStorageManager.isInAppMessageShown = true
    }

    override fun isInAppShown(): Boolean {
        return sessionStorageManager.isInAppMessageShown
    }
}