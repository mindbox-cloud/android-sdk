package cloud.mindbox.mobile_sdk.pushes

import android.app.Activity
import android.app.Notification.DEFAULT_ALL
import android.app.Notification.VISIBILITY_PRIVATE
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import java.net.URL
import kotlin.random.Random

internal object PushNotificationManager {

    private const val EXTRA_NOTIFICATION_ID = "notification_id"
    private const val EXTRA_URL = "push_url"
    private const val EXTRA_UNIQ_PUSH_KEY = "uniq_push_key"
    private const val EXTRA_UNIQ_PUSH_BUTTON_KEY = "uniq_push_button_key"
    private const val EXTRA_PAYLOAD = "push_payload"

    private const val MAX_ACTIONS_COUNT = 3
    private const val IMAGE_CONNECTION_TIMEOUT = 30000

    internal fun isNotificationsEnabled(
        context: Context,
    ): Boolean = LoggingExceptionHandler.runCatching(defaultValue = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (manager?.areNotificationsEnabled() != true) {
                return@runCatching false
            }
            manager.notificationChannels
                .firstOrNull { it.importance == NotificationManager.IMPORTANCE_NONE } == null
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    internal fun handleRemoteMessage(
        context: Context,
        remoteMessage: RemoteMessage,
        channelId: String,
        channelName: String,
        @DrawableRes pushSmallIcon: Int,
        channelDescription: String?,
        activities: Map<String, Class<out Activity>>?,
        defaultActivity: Class<out Activity>,
    ): Boolean = LoggingExceptionHandler.runCatching(defaultValue = false) {
        val correctedLinksActivities = activities?.mapKeys { (key, _) ->
            key.replace("*", ".*").toRegex()
        }

        val applicationContext = context.applicationContext
        val notificationId = Random.nextInt()

        val uniqueKey = remoteMessage.uniqueKey
        Mindbox.onPushReceived(applicationContext, uniqueKey)

        val pushActions = remoteMessage.pushActions
        val title = remoteMessage.title
        val text = remoteMessage.description
        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(pushSmallIcon)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(DEFAULT_ALL)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .handlePushClick(
                context,
                notificationId,
                uniqueKey,
                remoteMessage.payload,
                remoteMessage.pushLink,
                correctedLinksActivities,
                defaultActivity,
            )
            .handleActions(
                context,
                notificationId,
                uniqueKey,
                remoteMessage.payload,
                pushActions,
                correctedLinksActivities,
                defaultActivity,
            )
            .setNotificationStyle(remoteMessage.imageUrl, title, text)

        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(notificationManager, channelId, channelName, channelDescription)

        notificationManager.notify(notificationId, builder.build())

        true
    }

    internal fun getUniqKeyFromPushIntent(
        intent: Intent,
    ) = intent.getStringExtra(EXTRA_UNIQ_PUSH_KEY)

    internal fun getUniqPushButtonKeyFromPushIntent(
        intent: Intent,
    ) = intent.getStringExtra(EXTRA_UNIQ_PUSH_BUTTON_KEY)

    internal fun getUrlFromPushIntent(intent: Intent) = intent.getStringExtra(EXTRA_URL)

    internal fun getPayloadFromPushIntent(intent: Intent) = intent.getStringExtra(EXTRA_PAYLOAD)

    private fun createNotificationChannel(
        notificationManager: NotificationManager,
        channelId: String,
        channelName: String,
        channelDescription: String?,
    ) = LoggingExceptionHandler.runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                channelDescription.let { description = it }
                lockscreenVisibility = VISIBILITY_PRIVATE
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createPendingIntent(
        context: Context,
        activity: Class<out Activity>,
        id: Int,
        payload: String?,
        pushKey: String,
        url: String?,
        pushButtonKey: String? = null,
    ): PendingIntent? = LoggingExceptionHandler.runCatching(defaultValue = null) {
        val intent = getIntent(
            context = context,
            activity = activity,
            id = id,
            payload = payload,
            pushKey = pushKey,
            url = url,
            pushButtonKey = pushButtonKey,
        )

        val flags = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        PendingIntent.getActivity(
            context,
            Random.nextInt(),
            intent,
            flags,
        )
    }

    private fun NotificationCompat.Builder.handlePushClick(
        context: Context,
        notificationId: Int,
        uniqueKey: String,
        payload: String?,
        pushLink: String?,
        activities: Map<Regex, Class<out Activity>>?,
        defaultActivity: Class<out Activity>,
    ) = apply {
        val activity = resolveActivity(activities, pushLink, defaultActivity)
        createPendingIntent(
            context = context,
            activity = activity,
            id = notificationId,
            payload = payload,
            pushKey = uniqueKey,
            url = pushLink,
        )?.let(this::setContentIntent)
    }

    private fun NotificationCompat.Builder.handleActions(
        context: Context,
        notificationId: Int,
        uniqueKey: String,
        payload: String?,
        pushActions: List<PushAction>,
        activities: Map<Regex, Class<out Activity>>?,
        defaultActivity: Class<out Activity>,
    ) = apply {
        runCatching {
            pushActions.take(MAX_ACTIONS_COUNT).forEach { pushAction ->
                val activity = resolveActivity(activities, pushAction.url, defaultActivity)
                createPendingIntent(
                    context = context,
                    activity = activity,
                    id = notificationId,
                    pushKey = uniqueKey,
                    payload = payload,
                    url = pushAction.url,
                    pushButtonKey = pushAction.uniqueKey,
                )?.let { addAction(0, pushAction.text ?: "", it) }
            }
        }
    }

    private fun resolveActivity(
        activities: Map<Regex, Class<out Activity>>?,
        link: String?,
        defaultActivity: Class<out Activity>,
    ): Class<out Activity> {
        val key = link?.let { activities?.keys?.find { it.matches(link) } }
        return activities?.get(key) ?: defaultActivity
    }

    private fun NotificationCompat.Builder.setNotificationStyle(
        url: String?,
        title: String,
        text: String?,
    ) = apply {
        LoggingExceptionHandler.runCatching(
            block = {
                url.takeIf { !it.isNullOrEmpty() }?.let {
                    val connection = URL(url).openConnection().apply {
                        readTimeout = IMAGE_CONNECTION_TIMEOUT
                        connectTimeout = IMAGE_CONNECTION_TIMEOUT
                    }
                    BitmapFactory.decodeStream(connection.getInputStream())
                        ?.let { imageBitmap -> setImage(imageBitmap, title, text) }
                } ?: setText(text)
            },
            defaultValue = { setText(text) }
        )
    }

    private fun NotificationCompat.Builder.setImage(
        imageBitmap: Bitmap,
        title: String,
        text: String?,
    ): NotificationCompat.Builder {
        setLargeIcon(imageBitmap)

        val style = NotificationCompat.BigPictureStyle()
            .bigPicture(imageBitmap)
            .bigLargeIcon(null)
            .setBigContentTitle(title)
        text?.let(style::setSummaryText)

        return setStyle(style)
    }

    private fun NotificationCompat.Builder.setText(
        text: String?,
    ) = LoggingExceptionHandler.runCatching {
        setStyle(
            NotificationCompat.BigTextStyle()
                .bigText(text),
        )
    }

    private fun getIntent(
        context: Context,
        activity: Class<*>,
        id: Int,
        payload: String?,
        pushKey: String,
        url: String?,
        pushButtonKey: String?,
    ) = Intent(context, activity).apply {
        putExtra(EXTRA_PAYLOAD, payload)
        putExtra(Mindbox.IS_OPENED_FROM_PUSH_BUNDLE_KEY, true)
        putExtra(EXTRA_NOTIFICATION_ID, id)
        putExtra(EXTRA_UNIQ_PUSH_KEY, pushKey)
        putExtra(EXTRA_UNIQ_PUSH_BUTTON_KEY, pushButtonKey)
        url?.let { url -> putExtra(EXTRA_URL, url) }
        `package` = context.packageName
    }

}
