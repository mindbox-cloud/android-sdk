package cloud.mindbox.mobile_sdk.pushes

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle

/**
 * A class representing mindbox remote message
 * You can use it as a model to store data from mindbox
 * with your custom push notification implementation.
 * */
public data class MindboxRemoteMessage(
    val uniqueKey: String,
    val title: String,
    val description: String,
    val pushActions: List<PushAction>,
    val pushLink: String?,
    val imageUrl: String?,
    val payload: String?,
) {
    public companion object {
        public const val DATA_UNIQUE_KEY: String = "uniqueKey"
        public const val DATA_TITLE: String = "title"
        public const val DATA_MESSAGE: String = "message"
        public const val DATA_IMAGE_URL: String = "imageUrl"
        public const val DATA_BUTTONS: String = "buttons"
        public const val DATA_PUSH_CLICK_URL: String = "clickUrl"
        public const val DATA_PAYLOAD: String = "payload"
    }
}

public fun MindboxRemoteMessage.getPushContentIntent(
    context: Context,
    activity: Class<out Activity>,
    notificationId: Int,
    extras: Bundle? = null,
): PendingIntent? =
    PushNotificationManager.createPendingIntent(
        context = context,
        activity = activity,
        id = notificationId,
        payload = payload,
        pushKey = uniqueKey,
        url = pushLink,
        pushButtonKey = null,
        extras = extras,
    )

public fun MindboxRemoteMessage.getPushActionIntent(
    context: Context,
    activity: Class<out Activity>,
    notificationId: Int,
    pushAction: PushAction,
    extras: Bundle? = null,
): PendingIntent? =
    PushNotificationManager.createPendingIntent(
        context = context,
        activity = activity,
        id = notificationId,
        payload = payload,
        pushKey = uniqueKey,
        url = pushAction.url,
        pushButtonKey = pushAction.uniqueKey,
        extras = extras,
    )
