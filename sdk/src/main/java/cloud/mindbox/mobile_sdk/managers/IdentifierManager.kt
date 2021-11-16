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

    private const val ZERO_ID = "00000000-0000-0000-0000-000000000000"

    fun isNotificationsEnabled(context: Context): Boolean = runCatching {
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

    fun registerFirebaseToken(): String? = try {
        val token: String? = Tasks.await(FirebaseMessaging.getInstance().token)
        if (!token.isNullOrEmpty() && token != MindboxPreferences.firebaseToken) {
            MindboxLogger.i(this, "Token gets or updates from firebase")
        }
        token
    } catch (e: Exception) {
        MindboxLogger.w(this, "Fetching FCM registration token failed with exception $e")
        null
    }

    fun getAdsIdentification(context: Context): String = runCatching {
        val advertisingIdInfo = AdvertisingIdClient.getAdvertisingIdInfo(context)
        val id = advertisingIdInfo.id
        if (advertisingIdInfo.isLimitAdTrackingEnabled || id.isNullOrEmpty() || id == ZERO_ID) {
            MindboxLogger.d(
                this,
                "Device uuid cannot be received from AdvertisingIdClient. Will be generated from random. isLimitAdTrackingEnabled=${advertisingIdInfo.isLimitAdTrackingEnabled}, uuid from AdvertisingIdClient = $id"
            )
            generateRandomUuid()
        } else {
            MindboxLogger.d(
                this, "Received from AdvertisingIdClient: device uuid - $id"
            )
            id
        }
    }.returnOnException {
        MindboxLogger.d(
            this,
            "Device uuid cannot be received from AdvertisingIdClient. Will be generated from random"
        )
        generateRandomUuid()
    }

    fun generateRandomUuid() = UUID.randomUUID().toString()
}