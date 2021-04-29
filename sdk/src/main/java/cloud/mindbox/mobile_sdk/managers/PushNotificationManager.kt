package cloud.mindbox.mobile_sdk.managers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import cloud.mindbox.mobile_sdk.*
import cloud.mindbox.mobile_sdk.logOnException
import cloud.mindbox.mobile_sdk.models.PushAction
import cloud.mindbox.mobile_sdk.returnOnException
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.net.URL
import kotlin.random.Random

internal object PushNotificationManager {

    internal fun handleRemoteMessage(
        context: Context,
        remoteMessage: RemoteMessage?,
        channelId: String,
        channelName: String,
        channelDescription: String?
    ): Boolean {
        val applicationContext = context.applicationContext
        val gson = Gson()

        var title = "Pushok title"
        var description = "Empty message"

        val buttonsType = object : TypeToken<List<PushAction>>() {}.type
        val data = remoteMessage?.data

        val uniqueKey: String? = data?.get("uniqueKey")
        val imageUrl: String? = data?.get("imageUrl")
        val buttons = gson.fromJson<List<PushAction>>(
            data?.get("buttons").toString(),
            buttonsType
        )

        val imageBitmap = getImageByUrl(imageUrl)

        val pushAction = buttons?.firstOrNull() ?: return false

        /*val buttonIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("is_button", true)
            putExtra("uniq_push_key", uniqueKey)
            putExtra("uniq_push_button_key", pushAction.uniqueKey)
        }

        val buttonPendingIntent: PendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                0,
                buttonIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("is_button", false)
            putExtra("uniq_push_key", uniqueKey)
            putExtra("uniq_push_button_key", pushAction.uniqueKey)
        }

        val pendingIntent: PendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )*/

        if (!uniqueKey.isNullOrEmpty()) {

            Mindbox.onPushReceived(applicationContext, uniqueKey)

            title = remoteMessage.data["title"] ?: "Empty title"
            description = "uniq key: $uniqueKey"
        }

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(description)
            //.setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            //.addAction(R.drawable.ic_message, pushAction.text, buttonPendingIntent)
            .apply {
                /*val smallIconRes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    R.drawable.ic_launcher_foreground
                } else {
                    R.mipmap.ic_launcher_round
                }*/
                setSmallIcon(0)

                if (imageBitmap != null) {
                    setLargeIcon(imageBitmap)
                    setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(imageBitmap)
                            .bigLargeIcon(null)
                    )
                }
            }

        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(notificationManager, channelId, channelName, channelDescription)

        notificationManager.notify(Random.nextInt(), builder.build())

        return true
    }

    private fun createNotificationChannel(
        notificationManager: NotificationManager,
        channelId: String,
        channelName: String,
        channelDescription: String?
    ) = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                channelDescription.let { description = it }
            }

            notificationManager.createNotificationChannel(channel)
        }
    }.logOnException()

    private fun getImageByUrl(url: String?): Bitmap? = runCatching {
        if (url.isNullOrBlank()) {
            null
        } else {
            BitmapFactory.decodeStream(URL(url).openConnection().getInputStream())
        }
    }.returnOnException { null }

}
