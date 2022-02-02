package cloud.mindbox.mindbox_hms

import android.app.Activity
import android.content.Context
import androidx.annotation.DrawableRes
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.pushes.MindboxPushService
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.returnOnException
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage

object MindboxHuawei : MindboxPushService {

    override fun getServiceHandler(logger: MindboxLogger) = HuaweiServiceHandler(logger)

    /**
     * Handles only Mindbox notification message from [HmsMessageService].
     *
     * @param context context used for Mindbox initializing and push notification showing
     * @param message the [RemoteMessage] received from HMS
     * @param channelId the id of channel for Mindbox pushes
     * @param channelName the name of channel for Mindbox pushes
     * @param pushSmallIcon icon for push notification as drawable resource
     * @param channelDescription the description of channel for Mindbox pushes. Default is null
     * @param activities map (url mask) -> (Activity class). When clicked on push or button with url, corresponding activity will be opened
     *        Currently supports '*' character - indicator of zero or more numerical, alphabetic and punctuation characters
     *        e.g. mask "https://sample.com/" will match only "https://sample.com/" link
     *        whereas mask "https://sample.com/\u002A" will match
     *        "https://sample.com/", "https://sample.com/foo", "https://sample.com/foo/bar", "https://sample.com/foo?bar=baz" and other masks
     * @param defaultActivity default activity to be opened if url was not found in [activities]
     *
     * @return true if notification is Mindbox push and it's successfully handled, false otherwise.
     */
    @Suppress("Deprecation")
    fun handleRemoteMessage(
        context: Context,
        message: RemoteMessage?,
        channelId: String,
        channelName: String,
        @DrawableRes pushSmallIcon: Int,
        defaultActivity: Class<out Activity>,
        channelDescription: String? = null,
        activities: Map<String, Class<out Activity>>? = null,
    ): Boolean = runCatching {
        Mindbox.handleRemoteMessage(
            context,
            HuaweiRemoteMessageTransformer.transform(message),
            channelId,
            channelName,
            pushSmallIcon,
            defaultActivity,
            channelDescription,
            activities,
        )
    }.returnOnException { false }

}