package cloud.mindbox.mobile_sdk

import android.content.Context
import android.os.AsyncTask
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import java.util.*


class Configuration {

    fun getInstallationId(): String? {
        return MindboxPreferences.installationId
    }

    fun setInstallationId(value: UUID) {
        MindboxPreferences.installationId = value.toString()
    }

    fun getFirebaseToken(onResult: (String?) -> Unit) {
        if (MindboxPreferences.firebaseToken.isNullOrEmpty()) {
            Logger.e(this, "Firebase token will be registered")
            registerFirebaseToken { token ->
                onResult.invoke(token)
            }
        } else {
            Logger.i(this, "Firebase token gets from prefs")
            onResult.invoke(MindboxPreferences.firebaseToken)
        }
    }

    fun setFirebaseToken(value: String) {
        if (value.isNotEmpty() && value != MindboxPreferences.firebaseToken) {
            MindboxPreferences.firebaseToken = value
        }
    }

    fun registerFirebaseToken(onResult: ((String?) -> Unit)? = null) {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Logger.w(this, "Fetching FCM registration token failed ${task.exception}")
                    onResult?.invoke(null)
                    return@OnCompleteListener
                }

                val token = task.result
                if (!token.isNullOrEmpty() && token != MindboxPreferences.firebaseToken) {
                    Logger.i(this, "Token gets or updates from firebase")
                    MindboxPreferences.firebaseToken = token
                }
                onResult?.invoke(token)
            })
    }

    fun getDeviceUuid(context: Context, onResult: (String?) -> Unit) {
        if (MindboxPreferences.userAdid == null) {
            generateAdid(context) { uuid ->
                MindboxPreferences.userAdid = uuid
                onResult.invoke(uuid)
            }
        } else {
            onResult.invoke(MindboxPreferences.userAdid.toString())
        }
    }

    private fun generateAdid(context: Context, onResult: (String?) -> Unit) {
        AsyncTask.execute {
            try {
                val advertisingIdInfo = AdvertisingIdClient.getAdvertisingIdInfo(context)
                if (!advertisingIdInfo.isLimitAdTrackingEnabled && !advertisingIdInfo.id.isNullOrEmpty()) {
                    val id = advertisingIdInfo.id
                    onResult.invoke(id)
                    Logger.d(
                        this, "Generated: device uuid - $id"
                    )
                } else {
                    val id = generateRandomUuid()
                    Logger.d(
                        this,
                        "Device uuid cannot be generated from ads. Will be generated from Random - $id"
                    )
                    onResult.invoke(id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val id = generateRandomUuid()
                Logger.d(
                    this,
                    "Device uuid cannot be generated from ads. Will be generated from Random - $id"
                )
                onResult.invoke(id)
            }
        }
    }

    private fun generateRandomUuid() = UUID.randomUUID().toString()

}