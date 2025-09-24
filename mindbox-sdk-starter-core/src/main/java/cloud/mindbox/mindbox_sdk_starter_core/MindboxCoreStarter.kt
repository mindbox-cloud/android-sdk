package cloud.mindbox.mindbox_sdk_starter_core

import android.app.Activity
import android.app.Application
import android.content.Context
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.logger.Level

public object MindboxCoreStarter {

    private fun getNotificationData(application: Application): MindboxNotificationConfig =
        application.applicationContext.let { context ->
            MindboxNotificationConfig(
                channelId = context.getDefaultChannelId(),
                channelName = context.getDefaultChannelName(),
                channelDescription = context.getDefaultChannelDescription(),
                smallIcon = getDefaultSmallIcon(),
                defaultActivity = getLauncherActivity(application)
            )
        }

    /**
     * A method for internal sdk work only. Do not use it
     * */
    public fun handleMindboxRemoteMessage(application: Application, remoteMessage: Any?) {
        val notificationConfig = runCatching { getNotificationData(application) }.getOrElse { exception ->
            Mindbox.writeLog(exception.message.toString(), Level.ERROR)
            return
        }
        notificationConfig.defaultActivity?.let { defaultActivity ->
            Mindbox.handleRemoteMessage(
                context = application.applicationContext,
                activities = notificationConfig.activities,
                message = remoteMessage,
                channelId = notificationConfig.channelId,
                channelName = notificationConfig.channelName,
                pushSmallIcon = notificationConfig.smallIcon,
                defaultActivity = defaultActivity,
                channelDescription = notificationConfig.channelDescription
            )
        }
    }

    private fun getLauncherActivity(application: Application): Class<out Activity>? =
        application.packageManager
            .getLaunchIntentForPackage(application.packageName)
            ?.component
            ?.className
            ?.let { className ->
                Class.forName(className).asSubclass(Activity::class.java)
            }

    private fun Context.getDefaultChannelId(): String = getString(R.string.mindbox_default_channel_id)

    private fun Context.getDefaultChannelName(): String = getString(R.string.mindbox_default_channel_name)

    private fun Context.getDefaultChannelDescription(): String = getString(R.string.mindbox_default_channel_description)

    private fun getDefaultSmallIcon(): Int = R.drawable.mindbox_notification_small_icon
}
