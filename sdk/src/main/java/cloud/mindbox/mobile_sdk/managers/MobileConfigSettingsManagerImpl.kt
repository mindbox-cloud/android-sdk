package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import androidx.annotation.VisibleForTesting
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.UpdateData
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import cloud.mindbox.mobile_sdk.models.toTokenData
import cloud.mindbox.mobile_sdk.pushes.PushNotificationManager
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import cloud.mindbox.mobile_sdk.utils.loggingRunCatchingSuspending
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds

internal class MobileConfigSettingsManagerImpl(
    private val appContext: Context,
    private val sessionStorageManager: SessionStorageManager,
    private val timeProvider: TimeProvider,
    private val trackingIdsResolver: TrackingIdsResolver,
) : MobileConfigSettingsManager {

    override fun saveSessionTime(config: InAppConfigResponse) {
        config.settings?.slidingExpiration?.config?.interval
            ?.takeIf { it > 0 }
            ?.let { sessionTime ->
                sessionStorageManager.sessionTime = sessionTime.milliseconds
                mindboxLogI("Session time set to ${sessionStorageManager.sessionTime.inWholeMilliseconds} ms")
            } ?: mindboxLogI("SessionTime is not set")
    }

    override suspend fun checkPushTokenKeepalive(config: InAppConfigResponse): Unit = loggingRunCatchingSuspending {
        config.settings?.slidingExpiration?.pushTokenKeepalive?.interval
            ?.takeIf { it > 0 }
            ?.let { pushTokenKeepalive ->
                val lastInfoUpdateTime = MindboxPreferences.lastInfoUpdateTime
                val currentTime = timeProvider.currentTimeMillis()

                if (lastInfoUpdateTime == null) {
                    mindboxLogI("LastInfoUpdateTime is not set")
                } else if (lastInfoUpdateTime + pushTokenKeepalive > currentTime) {
                    mindboxLogI(
                        "Next ApplicationKeepalive = ${Date(lastInfoUpdateTime + pushTokenKeepalive)}, " +
                            "lastInfoUpdateTime = ${Date(lastInfoUpdateTime)}, " +
                            "pushTokenKeepalive = ${pushTokenKeepalive.milliseconds}"
                    )
                    return@loggingRunCatchingSuspending
                }

                sendAppKeepalive(pushTokenKeepalive)
            } ?: mindboxLogI("PushTokenKeepalive is not set")
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun sendAppKeepalive(pushTokenKeepalive: Long) {
        val pushTokens = MindboxPreferences.pushTokens
        val savedPushTokens = pushTokens.mapValues { it.value.token }
        val isNotificationEnabled = PushNotificationManager.isNotificationsEnabled(appContext)
        val infoUpdatedVersion = MindboxPreferences.infoUpdatedVersion

        val trackingIds = trackingIdsResolver.resolve(appContext)
        val updateData = UpdateData(
            isNotificationsEnabled = isNotificationEnabled,
            instanceId = MindboxPreferences.instanceId,
            version = infoUpdatedVersion,
            tokens = savedPushTokens.toTokenData(),
            trackingIds = trackingIds,
        )
        MindboxEventManager.appKeepalive(appContext, updateData)
        trackingIdsResolver.markSent(trackingIds)

        MindboxPreferences.isNotificationEnabled = isNotificationEnabled

        val nextSendTime = Date(timeProvider.currentTimeMillis() + pushTokenKeepalive)
        mindboxLogI(
            "Send ApplicationKeepalive: " +
                "next ApplicationKeepalive = $nextSendTime, " +
                "current pushTokenKeepalive = ${pushTokenKeepalive.milliseconds}"
        )
    }
}
