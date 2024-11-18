package com.mindbox.example.schedulePush

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import cloud.mindbox.mindbox_firebase.MindboxFirebase
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.logger.Level
import com.google.firebase.messaging.RemoteMessage
import com.mindbox.example.ActivityTransitionByPush
import com.mindbox.example.MainActivity
import com.mindbox.example.R
import java.util.Date
import kotlin.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.time.DurationUnit
import kotlin.time.toDuration

fun showMindboxNotification(applicationContext: Context, message: RemoteMessage): Boolean {
    val channelId = "mindbox_app_channel"
    val channelName = "defaultChannelName"
    val channelDescription = "defaultDescription"
    val pushSmallIcon = R.mipmap.ic_launcher

    // Listing of links and activities that should be opened by different links
    val activities = mapOf(
        "https://newActivity.com" to ActivityTransitionByPush::class.java
    )
    // Default Active. It will open if a link that is not in the list is received
    val defaultActivity = MainActivity::class.java

    // Method for rendering mobile push from Mindbox
    // Override resource mindbox_default_notification_color to change color pushSmallIcon
    val messageWasHandled = Mindbox.handleRemoteMessage(
        context = applicationContext,
        message = message,
        activities = activities,
        channelId = channelId,
        channelName = channelName,
        pushSmallIcon = pushSmallIcon,
        defaultActivity = defaultActivity,
        channelDescription = channelDescription
    )

    return messageWasHandled
}

fun scheduleMindboxNotification(context: Context, remoteMessage: RemoteMessage, delay: Duration) {
    val data = Data
        .Builder()
        .putAll(remoteMessage.data.mapValues { it.value.toString() })
        .build()

    val notificationWorkRequest: WorkRequest = OneTimeWorkRequestBuilder<MindboxNotificationWorker>()
        .setInitialDelay(delay.inWholeSeconds, TimeUnit.SECONDS)
        .setInputData(data)
        .build()

    runCatching {
        WorkManager.getInstance(context).enqueue(notificationWorkRequest)
    }.getOrElse {
        Mindbox.writeLog("Failed to schedule notification", Level.ERROR)
    }
}

fun handleMindboxRemoteMessage(applicationContext: Context, message: RemoteMessage): Boolean {
    val mindboxRemoteMessage = MindboxFirebase.convertToMindboxRemoteMessage(message) ?: return false

    val payload = mindboxRemoteMessage.getPayloadWithShowTime()

    return payload?.let {
        val delay: Duration = (payload.showTime.time - Date().time).toDuration(DurationUnit.MILLISECONDS)

        when {
            delay.absoluteValue <= payload.toleranceMinutes -> {
                Mindbox.writeLog("Show notification immediately because delay $delay in ${payload.toleranceMinutes} tolerance", Level.INFO)
                showMindboxNotification(applicationContext, message)
            }

            delay.isPositive() -> {
                Mindbox.writeLog("Schedule notification because delay $delay in the future", Level.INFO)
                scheduleMindboxNotification(applicationContext, message, delay)
                true
            }

            else -> {
                Mindbox.writeLog("Do not show notification because delay $delay in the past", Level.INFO)
                false
            }
        }
    } ?: showMindboxNotification(applicationContext, message)
}
