package com.mindbox.androidsdk

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cloud.mindbox.mobile_sdk.Mindbox
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random

class PushokMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Mindbox.updateFmsToken(applicationContext, token)
        super.onNewToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.i("Mindbox push", remoteMessage.data.toString())

        var title = "Pushok title"
        var description = "Empty message"

        val uniqueKey: String? = remoteMessage.data["uniqueKey"]

        if (!uniqueKey.isNullOrEmpty()) {

            Mindbox.onPushReceived(applicationContext, uniqueKey)

            title = remoteMessage.data["title"] ?: "Empty title"
            description = "uniq key: $uniqueKey"
        }

        val builder = NotificationCompat.Builder(this, getString(R.string.pushok_default_channel))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        createNotificationChannel()

        with(NotificationManagerCompat.from(this)) {
            // notificationId is a unique int for each notification that you must define
            notify(Random.nextInt(0, 100), builder.build())
        }

        super.onMessageReceived(remoteMessage)
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.pushok_default_channel)
            val descriptionText = getString(R.string.pushok_default_channel)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(getString(R.string.pushok_default_channel), name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}