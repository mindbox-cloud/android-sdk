package com.mindbox.example

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.putMindboxPushButtonExtras
import cloud.mindbox.mobile_sdk.putMindboxPushExtras
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

const val EXTRA_PUSH_URL = "com.mindbox.example.PUSH_URL"
const val EXTRA_PUSH_PAYLOAD = "com.mindbox.example.PUSH_PAYLOAD"

private const val MAX_ACTIONS_COUNT = 3
private const val IMAGE_TIMEOUT_MS = 5_000

fun showPushNotification(
    context: Context,
    push: ClientPush,
    channelId: String,
    channelName: String,
    channelDescription: String,
    @DrawableRes pushSmallIcon: Int,
    activities: Map<String, Class<out Activity>>,
    defaultActivity: Class<out Activity>,
) {
    Mindbox.onPushReceived(context, push.messageId)

    val notificationManager = NotificationManagerCompat.from(context)
    notificationManager.createNotificationChannel(
        NotificationChannelCompat.Builder(channelId, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(channelName)
            .setDescription(channelDescription)
            .build()
    )

    val notificationId = Random.nextInt()
    val image = loadImage(push)
    val notification = NotificationCompat.Builder(context, channelId)
        .setContentTitle(push.title)
        .setContentText(push.text)
        .setSmallIcon(pushSmallIcon)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(
            createPushIntent(
                context = context,
                activity = activities[push.clickUrl] ?: defaultActivity,
                push = push,
                link = push.clickUrl,
                button = null,
            )
        )
        .apply {
            push.buttons.take(MAX_ACTIONS_COUNT).forEach { button ->
                addAction(
                    0,
                    button.text,
                    createPushIntent(
                        context = context,
                        activity = activities[button.url] ?: defaultActivity,
                        push = push,
                        link = button.url,
                        button = button,
                    )
                )
            }
            if (image != null) {
                setLargeIcon(image)
                setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(image)
                        .bigLargeIcon(null as Bitmap?)
                        .setSummaryText(push.text)
                )
            } else {
                setStyle(NotificationCompat.BigTextStyle().bigText(push.text))
            }
        }
        .build()

    try {
        notificationManager.notify(notificationId, notification)
    } catch (e: SecurityException) {
        Log.w(Utils.TAG, "POST_NOTIFICATIONS is not granted, push ${push.messageId} is not shown", e)
    }
}

private fun createPushIntent(
    context: Context,
    activity: Class<out Activity>,
    push: ClientPush,
    link: String?,
    button: ClientPushButton?,
): PendingIntent {
    val intent = Intent(context, activity).apply {
        if (button == null) {
            putMindboxPushExtras(push.messageId)
        } else {
            putMindboxPushButtonExtras(push.messageId, button.uniqueKey)
        }
        putExtra(EXTRA_PUSH_URL, link)
        putExtra(EXTRA_PUSH_PAYLOAD, push.payload)
    }
    return PendingIntent.getActivity(
        context,
        Random.nextInt(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private fun loadImage(push: ClientPush): Bitmap? {
    val imageUrl = push.imageUrl ?: return null
    return runCatching {
        val connection = URL(imageUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = IMAGE_TIMEOUT_MS
            connection.readTimeout = IMAGE_TIMEOUT_MS
            connection.inputStream.use(BitmapFactory::decodeStream)
        } finally {
            connection.disconnect()
        }
    }.onFailure {
        Log.w(Utils.TAG, "Image of push ${push.messageId} is not loaded, showing push without it", it)
    }.getOrNull()
}
