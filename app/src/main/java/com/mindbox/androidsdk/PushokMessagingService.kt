package com.mindbox.androidsdk

import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cloud.mindbox.mobile_sdk.Mindbox
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random

class PushokMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Mindbox.updateFmsToken(token)
        super.onNewToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.i("Mindbox push", remoteMessage.data.toString())

        var title = "Pushok title"
        var description = "Empty message"

        if (remoteMessage.data.isNotEmpty()) {

            Mindbox.onPushReceived(
                applicationContext,
                remoteMessage.data["uniqueKey"] ?: "empty_unique_key"
            )

            title = remoteMessage.data["title"] ?: "Empty title"
            description = "uniq key: ${remoteMessage.data["title"] ?: "empty"}"
        }

        val builder = NotificationCompat.Builder(this, getString(R.string.pushok_default_channel))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(this)) {
            // notificationId is a unique int for each notification that you must define
            notify(Random.nextInt(0, 100), builder.build())
        }

        super.onMessageReceived(remoteMessage)
    }
}