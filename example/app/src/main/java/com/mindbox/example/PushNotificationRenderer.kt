package com.mindbox.example

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.pushes.getPushActionIntent
import cloud.mindbox.mobile_sdk.pushes.getPushContentIntent
import cloud.mindbox.mobile_sdk.pushes.handler.MessageHandlingState
import cloud.mindbox.mobile_sdk.pushes.handler.image.MindboxImageLoader
import cloud.mindbox.mobile_sdk.pushes.handler.image.default
import kotlin.random.Random

private const val MAX_ACTIONS_COUNT = 3

fun showPushNotification(
    context: Context,
    message: MindboxRemoteMessage,
    channelId: String,
    channelName: String,
    channelDescription: String,
    @DrawableRes pushSmallIcon: Int,
    activities: Map<String, Class<out Activity>>,
    defaultActivity: Class<out Activity>,
): Boolean {
    Mindbox.onPushReceived(context, message.uniqueKey)

    val notificationManager = NotificationManagerCompat.from(context)
    notificationManager.createNotificationChannel(
        NotificationChannelCompat.Builder(channelId, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(channelName)
            .setDescription(channelDescription)
            .build()
    )

    val notificationId = Random.nextInt()
    val image = loadImage(context, message)
    val notification = NotificationCompat.Builder(context, channelId)
        .setContentTitle(message.title)
        .setContentText(message.description)
        .setSmallIcon(pushSmallIcon)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(
            message.getPushContentIntent(
                context = context,
                activity = activities[message.pushLink] ?: defaultActivity,
                notificationId = notificationId,
            )
        )
        .apply {
            message.pushActions.take(MAX_ACTIONS_COUNT).forEach { pushAction ->
                addAction(
                    0,
                    pushAction.text.orEmpty(),
                    message.getPushActionIntent(
                        context = context,
                        activity = activities[pushAction.url] ?: defaultActivity,
                        notificationId = notificationId,
                        pushAction = pushAction,
                    )
                )
            }
            if (image != null) {
                setLargeIcon(image)
                setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(image)
                        .bigLargeIcon(null as Bitmap?)
                        .setSummaryText(message.description)
                )
            } else {
                setStyle(NotificationCompat.BigTextStyle().bigText(message.description))
            }
        }
        .build()

    return try {
        notificationManager.notify(notificationId, notification)
        true
    } catch (e: SecurityException) {
        Log.w(Utils.TAG, "POST_NOTIFICATIONS is not granted, push ${message.uniqueKey} is not shown", e)
        false
    }
}

private fun loadImage(context: Context, message: MindboxRemoteMessage): Bitmap? =
    runCatching {
        MindboxImageLoader.default().onLoadImage(
            context = context,
            message = message,
            state = MessageHandlingState(attemptNumber = 1, isMessageDisplayed = false),
        )
    }.onFailure {
        Log.w(Utils.TAG, "Image of push ${message.uniqueKey} is not loaded, showing push without it", it)
    }.getOrNull()
