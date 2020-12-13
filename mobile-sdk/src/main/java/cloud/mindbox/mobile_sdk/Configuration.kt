package cloud.mindbox.mobile_sdk

import android.content.Context
import android.util.Log
import androidx.ads.identifier.AdvertisingIdClient

class Configuration {

    internal fun generateAdid(context: Context) {
        if (AdvertisingIdClient.isAdvertisingIdProviderAvailable(context)) {
            val advertisingIdInfoListenableFuture =
                AdvertisingIdClient.getAdvertisingIdInfo(context)

            advertisingIdInfoListenableFuture.addListener({
                Log.d("Mindbox Debug", "Advertising load")
                val adInfo = advertisingIdInfoListenableFuture.get()
                val id: String? = adInfo?.id
                val providerPackageName: String? = adInfo?.providerPackageName
                val isLimitTrackingEnabled: Boolean? = adInfo?.isLimitAdTrackingEnabled
                Log.d(
                    "Mindbox Debug",
                    "Advertising info: id - $id; providerPackageName - $providerPackageName; isLimitTrackingEnabled - $isLimitTrackingEnabled"
                )
            },
                { command ->
                    command?.run()
                }
            )
        }
    }

}