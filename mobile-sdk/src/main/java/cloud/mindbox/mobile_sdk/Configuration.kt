package cloud.mindbox.mobile_sdk

import android.content.Context
import android.os.AsyncTask
import android.util.Log
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import java.util.*


class Configuration {

    fun getDeviceUuid(context: Context): String {
        val adid: String = if (MindboxPreferences.userAdid == null) {
            generateAdid(context).toString()
            //todo save it
        } else {
            MindboxPreferences.userAdid.toString()
        }

        return adid
    }

    internal fun generateAdid(context: Context) {
        AsyncTask.execute {
            try {
                val advertisingIdInfo = AdvertisingIdClient.getAdvertisingIdInfo(context)
                if (!advertisingIdInfo.isLimitAdTrackingEnabled && !advertisingIdInfo.id.isNullOrEmpty()) {
                    val id = advertisingIdInfo.id
                    Log.d(
                        "Mindbox Debug", "Generated: id - $id"
                    )
                } else {
                    Log.d(
                        "Mindbox Debug", "Generated: but limited id - ${generateRandomUuid()}"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d(
                    "Mindbox Debug",
                    "Advertising load is not available. Will be generated random"
                )
                Log.d(
                    "Mindbox Debug", "Generated: id - ${generateRandomUuid()}"
                )
            }
        }
    }

    private fun generateRandomUuid() = UUID.randomUUID().toString()

}