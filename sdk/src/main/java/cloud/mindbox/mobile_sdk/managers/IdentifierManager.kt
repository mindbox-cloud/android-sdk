package cloud.mindbox.mobile_sdk.managers

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.returnOnException
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import java.util.*

internal object IdentifierManager {

    fun isNotificationsEnabled(context: Context): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (manager?.areNotificationsEnabled() != true) {
                    return false
                }
                return manager.notificationChannels.firstOrNull { channel ->
                    channel.importance == NotificationManager.IMPORTANCE_NONE
                } == null
            } else {
                return NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }.returnOnException { true }
    }

    fun registerFirebaseToken(): String? {
        return try {
            val token: String? = Tasks.await(FirebaseMessaging.getInstance().token)
            if (!token.isNullOrEmpty() && token != MindboxPreferences.firebaseToken) {
                MindboxLogger.i(this, "Token gets or updates from firebase")
            }
            token
        } catch (e: Exception) {
            MindboxLogger.w(this, "Fetching FCM registration token failed with exception $e")
            null
        }
    }

    fun getAdsIdentification(context: Context): String {
        var id = ""
        try {
            val advertisingIdInfo = AdvertisingIdClient.getAdvertisingIdInfo(context)
            if (!advertisingIdInfo.isLimitAdTrackingEnabled && !advertisingIdInfo.id.isNullOrEmpty()) {
                id = advertisingIdInfo.id
                MindboxLogger.d(
                    this, "Received from AdvertisingIdClient: device uuid - $id"
                )
            } else {
                MindboxLogger.d(
                    this,
                    "Device uuid cannot be received from AdvertisingIdClient. Will be generated from Random - $id"
                )
            }
        } catch (exception: Exception) {
            MindboxLogger.e(
                this,
                "Device uuid cannot be received from AdvertisingIdClient. Will be generated from Random - $id",
                exception
            )
        } finally {
            return if (id.isNotEmpty()) id
            else generateRandomUuid()
        }
    }

    fun generateRandomUuid() = UUID.randomUUID().toString()
}