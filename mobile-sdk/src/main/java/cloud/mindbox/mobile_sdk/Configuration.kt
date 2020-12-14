package cloud.mindbox.mobile_sdk

import android.content.Context
import android.os.AsyncTask
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import java.util.*


class Configuration {

    fun getInstallationId(): String? {
        return MindboxPreferences.installationId
    }

    fun setInstallationId(value: UUID) {
        MindboxPreferences.installationId = value.toString()
    }

    fun getFirebaseToken(): String? {
        return MindboxPreferences.firebaseToken
    }

    fun setFirebaseToken(value: String) {
        MindboxPreferences.firebaseToken = value
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