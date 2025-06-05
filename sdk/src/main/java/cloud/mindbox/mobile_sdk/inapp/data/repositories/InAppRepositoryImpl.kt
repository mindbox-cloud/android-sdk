package cloud.mindbox.mobile_sdk.inapp.data.repositories

import android.content.Context
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.SystemTimeProvider
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

internal class InAppRepositoryImpl(
    private val context: Context,
    private val sessionStorageManager: SessionStorageManager,
    private val inAppSerializationManager: InAppSerializationManager,
    private val timeProvider: SystemTimeProvider
) : InAppRepository {

    companion object {
        private const val IN_APP_SHOWN_EXPIRATION_DAYS = 2L
    }

    override fun saveCurrentSessionInApps(inApps: List<InApp>) {
        sessionStorageManager.currentSessionInApps = inApps
    }

    override fun getCurrentSessionInApps(): List<InApp> {
        return sessionStorageManager.currentSessionInApps
    }

    override fun getTargetedInApps(): Map<String, MutableSet<Int>> {
        return sessionStorageManager.shownInAppIdsWithEvents
    }

    override fun saveTargetedInAppWithEvent(inAppId: String, eventHashcode: Int) {
        sessionStorageManager.shownInAppIdsWithEvents[inAppId] =
            sessionStorageManager.shownInAppIdsWithEvents.getOrElse(inAppId) {
                mutableSetOf()
            }.apply {
                add(eventHashcode)
            }
    }

    override fun saveUnShownOperationalInApp(operation: String, inApp: InApp) {
        sessionStorageManager.unShownOperationalInApps[operation] =
            sessionStorageManager.unShownOperationalInApps.getOrElse(operation) {
                mutableListOf()
            }.apply {
                add(inApp)
            }
    }

    override fun getUnShownOperationalInAppsByOperation(operation: String): List<InApp> {
        return sessionStorageManager.unShownOperationalInApps[operation.lowercase()] ?: emptyList()
    }

    override fun saveOperationalInApp(operation: String, inApp: InApp) {
        sessionStorageManager.operationalInApps[operation] =
            sessionStorageManager.operationalInApps.getOrElse(operation) {
                mutableListOf()
            }.apply {
                add(inApp)
            }
    }

    override fun getOperationalInAppsByOperation(operation: String): List<InApp> {
        return sessionStorageManager.operationalInApps[operation.lowercase()] ?: emptyList()
    }

    override fun getShownInApps(): Map<String, List<Long>> {
        return inAppSerializationManager.deserializeToShownInAppsMap(MindboxPreferences.shownInApps)
    }

    override fun listenInAppEvents(): Flow<InAppEventType> {
        return MindboxEventManager.eventFlow
    }

    override fun saveShownInApp(id: String, timeStamp: Long) {
        val shownInApps = getShownInApps().toMutableMap()
        val currentTime = timeProvider.currentTimeMillis()

        shownInApps[id] = (shownInApps[id] ?: emptyList())
            .filter { currentTime - it <= TimeUnit.DAYS.toMillis(IN_APP_SHOWN_EXPIRATION_DAYS) }
            .plus(timeStamp)

        inAppSerializationManager.serializeToShownInAppsString(shownInApps).also {
            if (it.isNotBlank()) {
                MindboxPreferences.shownInApps = it
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

    override fun isInAppShown(inAppId: String): Boolean {
        return sessionStorageManager.inAppMessageShownInSession.any { it == inAppId }
    }

    override fun clearInAppEvents() {
        MindboxEventManager.resetEventFlowCache()
    }

    override fun isTimeDelayInapp(inAppId: String): Boolean =
        sessionStorageManager.currentSessionInApps
            .any { it.id == inAppId && it.frequency.delay is Frequency.Delay.TimeDelay }

    override fun setInAppShown(inAppId: String) {
        sessionStorageManager.inAppMessageShownInSession.add(inAppId)
    }
}
