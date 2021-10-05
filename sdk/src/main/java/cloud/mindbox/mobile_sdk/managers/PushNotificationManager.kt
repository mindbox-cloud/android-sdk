package cloud.mindbox.mobile_sdk.managers

import android.app.Activity
import android.app.Notification.DEFAULT_ALL
import android.app.Notification.VISIBILITY_PRIVATE
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.logOnException
import cloud.mindbox.mobile_sdk.models.PushAction
import cloud.mindbox.mobile_sdk.returnOnException
import cloud.mindbox.mobile_sdk.services.MindboxPushReceiver.Companion.getIntent
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.net.URL
import kotlin.random.Random

internal object PushNotificationManager {

    private const val DATA_UNIQUE_KEY = "uniqueKey"
    private const val DATA_TITLE = "title"
    private const val DATA_MESSAGE = "message"
    private const val DATA_IMAGE_URL = "imageUrl"
    private const val DATA_BUTTONS = "buttons"
    private const val DATA_PUSH_CLICK_URL = "clickUrl"
    private const val MAX_ACTIONS_COUNT = 3
    private const val IMAGE_CONNECTION_TIMEOUT = 30000

    internal fun handleRemoteMessage(
        context: Context,
        activities: Map<String, Class<out Activity>>,
        remoteMessage: RemoteMessage?,
        channelId: String,
        channelName: String,
        @DrawableRes pushSmallIcon: Int,
        channelDescription: String?
    ): Boolean = runCatching {
        val correctedLinksActivities = activities.mapKeys { (key , _) ->
            key.replace("*", "[0-9\\p{L}//?.]*").toRegex()
        }

        val data = remoteMessage?.data ?: return false
        val uniqueKey = data[DATA_UNIQUE_KEY] ?: return false

        val applicationContext = context.applicationContext
        val title = data[DATA_TITLE] ?: ""
        val description = data[DATA_MESSAGE] ?: ""
        val pushActionsType = object : TypeToken<List<PushAction>>() {}.type
        val pushActions = Gson().fromJson<List<PushAction>>(data[DATA_BUTTONS], pushActionsType)
        val notificationId = Random.nextInt()
        val pushLink = data[DATA_PUSH_CLICK_URL]

        Mindbox.onPushReceived(applicationContext, uniqueKey)

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(description)
            .setSmallIcon(pushSmallIcon)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(DEFAULT_ALL)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .handlePushClick(context, correctedLinksActivities, notificationId, uniqueKey, pushLink)
            .handleActions(context, correctedLinksActivities, notificationId, uniqueKey, pushActions, pushLink)
            .handleImageByUrl(data[DATA_IMAGE_URL], title, description)

        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(notificationManager, channelId, channelName, channelDescription)

        notificationManager.notify(notificationId, builder.build())

        return true
    }.returnOnException { false }

    private fun createNotificationChannel(
        notificationManager: NotificationManager,
        channelId: String,
        channelName: String,
        channelDescription: String?
    ) = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                channelDescription.let { description = it }
                lockscreenVisibility = VISIBILITY_PRIVATE
            }

            notificationManager.createNotificationChannel(channel)
        }
    }.logOnException()

    private fun createPendingIntent(
        context: Context,
        activity: Class<out Activity>,
        id: Int,
        pushKey: String,
        url: String?,
        pushButtonKey: String? = null
    ): PendingIntent? = runCatching {
        val intent = getIntent(context, activity, id, pushKey, url, pushButtonKey)

        val flags = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        PendingIntent.getActivity(
            context,
            Random.nextInt(),
            intent,
            flags
        )
    }.returnOnException { null }

    private fun NotificationCompat.Builder.handlePushClick(
        context: Context,
        activities: Map<Regex, Class<out Activity>>,
        notificationId: Int,
        uniqueKey: String,
        pushLink: String?
    ) = apply {
        val key = pushLink?.let { activities.keys.find { it.matches(pushLink) } }
        activities[key]?.let { activity ->
            createPendingIntent(
                context = context,
                activity = activity,
                id = notificationId,
                pushKey = uniqueKey,
                url = pushLink
            )?.let(this::setContentIntent)
        }
    }

    private fun NotificationCompat.Builder.handleActions(
        context: Context,
        activities: Map<Regex, Class<out Activity>>,
        notificationId: Int,
        uniqueKey: String,
        pushActions: List<PushAction>,
        pushLink: String?
    ) = apply {
        runCatching {
            pushActions.take(MAX_ACTIONS_COUNT).forEach { pushAction ->
                val activity = pushAction.url?.let {
                    val key = activities.keys.find { it.matches(pushAction.url) }
                        ?: pushLink?.let { activities.keys.find { it.matches(pushLink) } }
                    activities[key]
                }
                activity?.let {
                    createPendingIntent(
                        context = context,
                        activity = activity,
                        id = notificationId,
                        pushKey = uniqueKey,
                        url = pushAction.url, // TODO: Or pushLink if activity was not found for key pushAction.url
                        pushButtonKey = pushAction.uniqueKey
                    )?.let { addAction(0, pushAction.text ?: "", it) }
                }
            }
        }
    }

    private fun NotificationCompat.Builder.handleImageByUrl(
        url: String?,
        title: String,
        text: String?
    ) = apply {
        runCatching {
            if (!url.isNullOrBlank()) {
                val connection = URL(url).openConnection().apply {
                    readTimeout = IMAGE_CONNECTION_TIMEOUT
                    connectTimeout = IMAGE_CONNECTION_TIMEOUT
                }
                BitmapFactory.decodeStream(connection.getInputStream())
                    ?.let { imageBitmap ->
                        setLargeIcon(imageBitmap)

                        val style = NotificationCompat.BigPictureStyle()
                            .bigPicture(imageBitmap)
                            .bigLargeIcon(null)
                            .setBigContentTitle(title)
                        text?.let(style::setSummaryText)

                        setStyle(style)
                    }
            }
        }.logOnException()
    }

}
