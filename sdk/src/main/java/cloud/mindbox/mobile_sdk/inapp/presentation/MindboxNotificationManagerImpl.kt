package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import cloud.mindbox.mobile_sdk.inapp.presentation.actions.PushActivationActivity
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.managers.RequestPermissionManager
import cloud.mindbox.mobile_sdk.utils.Constants
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler

internal class MindboxNotificationManagerImpl(
    private val context: Context,
    private val requestPermissionManager: RequestPermissionManager
) : MindboxNotificationManager {

    override var shouldOpenSettings: Boolean = true

    override fun isNotificationEnabled(): Boolean {
        return runCatching {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }.getOrElse { _ ->
            mindboxLogE("Unknown error checking notification permission status")
            true
        }
    }

    override fun openNotificationSettings(activity: Activity) {
        LoggingExceptionHandler.runCatching {
            val intent = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                    }
                }

                else -> {
                    Intent(Constants.NOTIFICATION_SETTINGS).apply {
                        putExtra(Constants.APP_PACKAGE_NAME, activity.packageName)
                        putExtra(Constants.APP_UID_NAME, activity.applicationInfo.uid)
                    }
                }
            }
            mindboxLogI("Opening notification settings.")
            activity.startActivity(intent)
        }
    }

    override fun requestPermission(activity: Activity) {
        LoggingExceptionHandler.runCatching {
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                mindboxLogI("Notification is enabled now, don't try request permission")
                return@runCatching
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissionManager.increaseRequestCounter()
                if (activity.shouldShowRequestPermissionRationale(Constants.POST_NOTIFICATION)) {
                    shouldOpenSettings = false
                }

                val intent = Intent(activity, PushActivationActivity::class.java)
                activity.startActivity(intent)
            } else {
                openNotificationSettings(activity)
            }
        }
    }
}
