package cloud.mindbox.mobile_sdk.inapp.data.repositories

import android.content.Context
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.SessionManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import kotlinx.coroutines.flow.Flow

internal class InAppRepositoryImpl(
    private val sessionManager: SessionManager,
    private val context: Context,
    private val inAppSerializationManager: InAppSerializationManager,
) :
    InAppRepository {

    override fun saveOperationalInApp(operation: String, inApp: InApp) {
        LoggingExceptionHandler.runCatching {
            val list = sessionManager.operationalInApps.putIfAbsent(operation, mutableListOf(inApp))
                ?: return@runCatching
            list.add(inApp)
            sessionManager.operationalInApps[operation] = list
        }
    }

    override fun getOperationalInAppsByOperation(operation: String): List<InApp> {
        return LoggingExceptionHandler.runCatching(emptyList()) {
            sessionManager.operationalInApps[operation] ?: emptyList()
        }
    }

    override fun getShownInApps(): HashSet<String> {
        return LoggingExceptionHandler.runCatching(HashSet()) {
            if (MindboxPreferences.shownInAppIds.isBlank()) {
                HashSet()
            } else {
                inAppSerializationManager.deserializeToShownInApps(MindboxPreferences.shownInAppIds)
            }
        }
    }

    override fun listenInAppEvents(): Flow<InAppEventType> {
        return MindboxEventManager.eventFlow
    }

    override fun saveShownInApp(id: String) {
        val shownInApps = getShownInApps().apply { add(id) }
        inAppSerializationManager.serializeToShownInAppsString(shownInApps, id).apply {
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
                    this)
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
}