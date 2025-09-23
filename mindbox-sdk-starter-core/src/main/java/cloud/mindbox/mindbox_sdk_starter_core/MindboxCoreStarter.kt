package cloud.mindbox.mindbox_sdk_starter_core

import android.app.Activity
import android.app.Application
import android.content.Context

public object MindboxCoreStarter {

    /**
     * A method for internal sdk work only. Do not use it
     * */
    public fun getNotificationData(application: Application): MindboxNotificationConfig =
        application.applicationContext.let { context ->
            MindboxNotificationConfig(
                channelId = context.getDefaultChannelId(),
                channelName = context.getDefaultChannelName(),
                channelDescription = context.getDefaultChannelDescription(),
                smallIcon = getDefaultSmallIcon(),
                defaultActivity = getLauncherActivity(application)
            )
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
