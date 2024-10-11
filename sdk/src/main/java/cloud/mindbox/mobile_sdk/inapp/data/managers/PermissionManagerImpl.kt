package cloud.mindbox.mobile_sdk.inapp.data.managers

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionManager
import cloud.mindbox.mobile_sdk.logger.mindboxLogE

internal class PermissionManagerImpl(private val context: Context) : PermissionManager {
    override fun isNotificationEnabled(): Boolean {
        return runCatching {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }.getOrElse { _ ->
            mindboxLogE("Unknown error checking notification permission status")
            true
        }
    }
}
