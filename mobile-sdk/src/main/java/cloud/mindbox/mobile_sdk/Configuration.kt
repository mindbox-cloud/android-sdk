package cloud.mindbox.mobile_sdk

import android.content.Context
import android.os.AsyncTask
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
                    Logger.i(
                        this, "Generated: id - $id"
                    )
                } else {
                    Logger.i(
                        this, "Generated: but limited id - ${generateRandomUuid()}"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Logger.i(
                    this, "Advertising load is not available. Will be generated random"
                )
                Logger.i(
                    this, "Generated: id - ${generateRandomUuid()}"
                )
            }
        }
    }

    private fun generateRandomUuid() = UUID.randomUUID().toString()

}