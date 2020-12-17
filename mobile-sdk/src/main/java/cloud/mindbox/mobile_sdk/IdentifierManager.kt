package cloud.mindbox.mobile_sdk

import android.content.Context
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import java.util.*

//todo remove logs and add docs
internal object IdentifierManager {

    fun getFirebaseToken(): String? {
        return if (MindboxPreferences.firebaseToken.isNullOrEmpty()) {
            Logger.i(this, "Firebase token will be registered")
            registerFirebaseToken()
        } else {
            Logger.i(this, "Firebase token gets from prefs")
            MindboxPreferences.firebaseToken
        }
    }

    fun checkFirebaseTokenUpdates() {
        val oldToken = MindboxPreferences.firebaseToken
        val newToken = registerFirebaseToken()
        if (oldToken != newToken && newToken != null) {
            //todo send to server
        }
    }

    fun getAdsIdentification(context: Context?): String? {
        return if (context == null) {
            Logger.e(this, "Mindbox SDK is not initialized")
            null
        } else {
            if (MindboxPreferences.userAdid == null) {
                val adid = generateAdsId(context)
                MindboxPreferences.userAdid = adid
                adid
            } else {
                MindboxPreferences.userAdid.toString()
            }
        }
    }

    private fun registerFirebaseToken(): String? {
        return try {
            val token: String? = Tasks.await(FirebaseMessaging.getInstance().token)
            if (!token.isNullOrEmpty() && token != MindboxPreferences.firebaseToken) {
                Logger.i(this, "Token gets or updates from firebase")
                MindboxPreferences.firebaseToken = token
            }
            token
        } catch (e: Exception) {
            Logger.w(this, "Fetching FCM registration token failed with exception $e")
            null
        }
    }

    private fun generateAdsId(context: Context): String {
        return try {
            val advertisingIdInfo = AdvertisingIdClient.getAdvertisingIdInfo(context)
            if (!advertisingIdInfo.isLimitAdTrackingEnabled && !advertisingIdInfo.id.isNullOrEmpty()) {
                val id = advertisingIdInfo.id
                Logger.d(
                    this, "Generated: device uuid - $id"
                )
                id
            } else {
                val id = generateRandomUuid()
                Logger.d(
                    this,
                    "Device uuid cannot be generated from ads. Will be generated from Random - $id"
                )
                id
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val id = generateRandomUuid()
            Logger.d(
                this,
                "Device uuid cannot be generated from ads. Will be generated from Random - $id"
            )
            id
        }
    }

    private fun generateRandomUuid() = UUID.randomUUID().toString()
}