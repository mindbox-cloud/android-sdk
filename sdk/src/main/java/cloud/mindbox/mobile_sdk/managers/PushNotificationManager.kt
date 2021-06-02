package cloud.mindbox.mobile_sdk.managers

import android.app.Notification.DEFAULT_ALL
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
import cloud.mindbox.mobile_sdk.services.MindboxPushReceiver.Companion.ACTION_CLICKED
import cloud.mindbox.mobile_sdk.services.MindboxPushReceiver.Companion.EXTRA_URL
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
    private const val MAX_ACTIONS_COUNT = 3
    private const val IMAGE_CONNECTION_TIMEOUT = 30000

    internal fun handleRemoteMessage(
        context: Context,
        remoteMessage: RemoteMessage?,
        channelId: String,
        channelName: String,
        @DrawableRes pushSmallIcon: Int,
        channelDescription: String?
    ): Boolean = runCatching {
        val data = remoteMessage?.data ?: return false
        val uniqueKey = data[DATA_UNIQUE_KEY] ?: return false

        val applicationContext = context.applicationContext
        val title = data[DATA_TITLE] ?: ""
        val description = data[DATA_MESSAGE] ?: ""
        val pushActionsType = object : TypeToken<List<PushAction>>() {}.type
        val pushActions = Gson().fromJson<List<PushAction>>(data[DATA_BUTTONS], pushActionsType)
        val notificationId = Random.nextInt()

        Mindbox.onPushReceived(applicationContext, uniqueKey)

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(description)
            .setSmallIcon(pushSmallIcon)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(DEFAULT_ALL)
            .setAutoCancel(true)
            .handlePushClick(context, notificationId, uniqueKey)
            .handleActions(context, notificationId, uniqueKey, pushActions)
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
            }

            notificationManager.createNotificationChannel(channel)
        }
    }.logOnException()

    private fun createPendingIntent(
        context: Context,
        id: Int,
        action: String,
        pushKey: String,
        pushAction: PushAction? = null
    ): PendingIntent? = runCatching {
        val intent = getIntent(context, id, action, pushKey, pushAction?.uniqueKey).apply {
            pushAction?.url?.let { url -> putExtra(EXTRA_URL, url) }
        }

        PendingIntent.getBroadcast(
            context,
            Random.nextInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }.returnOnException { null }

    private fun NotificationCompat.Builder.handlePushClick(
        context: Context,
        notificationId: Int,
        uniqueKey: String
    ) = apply {
        createPendingIntent(
            context = context,
            id = notificationId,
            action = ACTION_CLICKED,
            pushKey = uniqueKey
        )?.let(this::setContentIntent)
    }

    private fun NotificationCompat.Builder.handleActions(
        context: Context,
        notificationId: Int,
        uniqueKey: String,
        pushActions: List<PushAction>
    ) = apply {
        runCatching {
            pushActions.take(MAX_ACTIONS_COUNT).forEach { pushAction ->
                createPendingIntent(
                    context = context,
                    id = notificationId,
                    action = ACTION_CLICKED,
                    pushKey = uniqueKey,
                    pushAction = pushAction
                )?.let { addAction(0, pushAction.text ?: "", it) }
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
