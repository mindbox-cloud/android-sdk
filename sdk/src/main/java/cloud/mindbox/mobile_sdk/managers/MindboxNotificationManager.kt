package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import cloud.mindbox.mobile_sdk.logger.mindboxLogE

internal class MindboxNotificationManager(val context: Context) {
    fun isNotificationEnabled(): Boolean {
        return runCatching {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }.getOrElse { _ ->
            mindboxLogE("Unknown error checking notification permission status")
            true
        }
    }
}