package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import androidx.annotation.VisibleForTesting
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.UpdateData
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import cloud.mindbox.mobile_sdk.models.toTokenData
import cloud.mindbox.mobile_sdk.parseTimeSpanToMillis
import cloud.mindbox.mobile_sdk.pushes.PushNotificationManager
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds

internal class MobileConfigSettingsManagerImpl(
    private val appContext: Context,
    private val sessionStorageManager: SessionStorageManager,
    private val timeProvider: TimeProvider
) : MobileConfigSettingsManager {

    override fun saveSessionTime(config: InAppConfigResponse) {
        config.settings?.slidingExpiration?.config?.parseTimeSpanToMillis()?.let { sessionTime ->
            if (sessionTime > 0) {
                sessionStorageManager.sessionTime = sessionTime.milliseconds
                mindboxLogI("Session time set to ${sessionStorageManager.sessionTime.inWholeMilliseconds} ms")
            }
        }
    }

    override fun checkPushTokenKeepALive(config: InAppConfigResponse): Unit = loggingRunCatching {
        config.settings?.slidingExpiration?.pushTokenKeepALive?.parseTimeSpanToMillis()
            ?.takeIf { it > 0 }
            ?.let { pushTokenKeepALive ->
                val lastInfoUpdateTime = MindboxPreferences.lastInfoUpdateTime
                val currentTime = timeProvider.currentTimeMillis()

                if (lastInfoUpdateTime == null) {
                    mindboxLogI("LastInfoUpdateTime is not set")
                } else if (lastInfoUpdateTime + pushTokenKeepALive > currentTime) {
                    mindboxLogI(
                        "Next ApplicationKeepalive = ${Date(lastInfoUpdateTime + pushTokenKeepALive)}, " +
                            "lastInfoUpdateTime = ${Date(lastInfoUpdateTime)}, " +
                            "pushTokenKeepALive = ${pushTokenKeepALive.milliseconds}"
                    )
                    return@loggingRunCatching
                }

                sendAppKeepAlive(pushTokenKeepALive)
            } ?: mindboxLogI("PushTokenKeepALive is not set")
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun sendAppKeepAlive(pushTokenKeepALive: Long) {
        val pushTokens = MindboxPreferences.pushTokens
        val savedPushTokens = pushTokens.mapValues { it.value.token }
        val isNotificationEnabled = PushNotificationManager.isNotificationsEnabled(appContext)
        val infoUpdatedVersion = MindboxPreferences.infoUpdatedVersion

        val updateData = UpdateData(
            isNotificationsEnabled = isNotificationEnabled,
            instanceId = MindboxPreferences.instanceId,
            version = infoUpdatedVersion,
            tokens = savedPushTokens.toTokenData(),
        )
        MindboxEventManager.appKeepAlive(appContext, updateData)

        MindboxPreferences.isNotificationEnabled = isNotificationEnabled

        val nextSendTime = Date(timeProvider.currentTimeMillis() + pushTokenKeepALive)
        mindboxLogI(
            "Send ApplicationKeepalive: " +
                "next ApplicationKeepalive = $nextSendTime, " +
                "current pushTokenKeepALive = ${pushTokenKeepALive.milliseconds}"
        )
    }
}
