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

/**
 * Creates a [PendingIntent] for the push notification content.
 *
 * This method creates a PendingIntent with the unique push key in the extras.
 * This is used by the Mindbox SDK to properly identify and handle a push notification clicks that does not include any push buttons.
 *
 * Note: Use this method to get the PendingIntent for [androidx.core.app.NotificationCompat.Builder.setContentIntent].
 *
 * @param context The context used to create the PendingIntent.
 * @param activity The activity class to be launched when the notification is clicked.
 * @param notificationId The unique ID of the notification.
 * @param extras Additional data to be included in the intent.
 * @return A [PendingIntent] to be used with [androidx.core.app.NotificationCompat.Builder.setContentIntent].
 */
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

/**
 * Creates a [PendingIntent] for the push notification action button.
 *
 * This method creates a PendingIntent with both the unique push key and the unique push button key in the extras.
 * These are used by the Mindbox SDK to properly identify and handle push notification button clicks.
 *
 * Note: Use this method to get the PendingIntent for [androidx.core.app.NotificationCompat.Builder.addAction].
 *
 * @param context The context used to create the PendingIntent.
 * @param activity The activity class to be launched when the action is clicked.
 * @param notificationId The unique ID of the notification.
 * @param pushAction The action object containing the button's unique key and other details.
 * @param extras Additional data to be included in the intent.
 * @return A [PendingIntent] to be used with [androidx.core.app.NotificationCompat.Builder.addAction].
 */
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
