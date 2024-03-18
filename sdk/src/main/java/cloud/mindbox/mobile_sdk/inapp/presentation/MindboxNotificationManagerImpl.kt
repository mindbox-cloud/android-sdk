package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import cloud.mindbox.mobile_sdk.inapp.presentation.actions.PermissionCallback
import cloud.mindbox.mobile_sdk.inapp.presentation.actions.PushActivationActivity
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.utils.Constants
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler

internal class MindboxNotificationManagerImpl(private val context: Context) : MindboxNotificationManager {

    private var permissionCallback: PermissionCallback? = null
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

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                    Intent("android.settings.APP_NOTIFICATION_SETTINGS").apply {
                        putExtra("app_package", activity.packageName)
                        putExtra("app_uid", activity.applicationInfo.uid)
                    }
                }

                else -> {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", activity.packageName, null)
                    }
                }
            }
            mindboxLogI("Opening notification settings.")
            activity.startActivity(intent)
        }
    }

    override fun requestPermission(activity: Activity) {
        LoggingExceptionHandler.runCatching {
            if (isNotificationEnabled()) {
                mindboxLogI("Notification is enabled now, don't try request permission")
                return@runCatching
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                permissionCallback = PermissionCallback {
                    openNotificationSettings(activity)
                }

                if (activity.shouldShowRequestPermissionRationale(Constants.POST_NOTIFICATION)) {
                    mindboxLogI("The second request of permission")
                    activity.requestPermissions(
                        arrayOf(Constants.POST_NOTIFICATION),
                        PERMISSION_REQUEST_CODE
                    )
                } else {
                    val intent = Intent(activity, PushActivationActivity::class.java)
                    activity.startActivity(intent)
                }

            } else {
                openNotificationSettings(activity)
            }
        }
    }

    override fun callCallback() {
        permissionCallback?.onNeedShowSettings()
        permissionCallback = null
    }

    companion object {
        private val PERMISSION_REQUEST_CODE = 125129
    }
}