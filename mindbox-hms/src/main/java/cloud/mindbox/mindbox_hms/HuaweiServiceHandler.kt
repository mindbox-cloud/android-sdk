package cloud.mindbox.mindbox_hms

import android.content.Context
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import com.huawei.agconnect.AGConnectOptionsBuilder
import com.huawei.hms.aaid.HmsInstanceId
import com.huawei.hms.api.ConnectionResult
import com.huawei.hms.api.HuaweiApiAvailability
import com.huawei.hms.push.HmsMessaging
import kotlinx.coroutines.delay
import com.huawei.hms.ads.identifier.AdvertisingIdClient

class HuaweiServiceHandler(private val logger: MindboxLogger) : PushServiceHandler() {

    companion object {
        private const val HMS_APP_ID_KEY = "client/app_id"
        private const val HMS_TOKEN_SCOPE = "HCM"
        private const val TOKEN_ACQUISITION_DELAY = 2000L
    }

    override val notificationProvider: String = "HCM"

    override fun initService(context: Context) {
        HmsMessaging.getInstance(context).isAutoInitEnabled = true
    }

    override suspend fun getToken(context: Context): String? {
        val appId = AGConnectOptionsBuilder().build(context).getString(HMS_APP_ID_KEY)
        val hms = HmsInstanceId.getInstance(context)
        return hms.getToken(appId, HMS_TOKEN_SCOPE)?.takeIf(String::isNotEmpty) ?: run {
            delay(TOKEN_ACQUISITION_DELAY)
            hms.getToken(appId, HMS_TOKEN_SCOPE)
        }
    }

    override fun getAdsId(context: Context): Pair<String?, Boolean> {
        val info: AdvertisingIdClient.Info? = AdvertisingIdClient.getAdvertisingIdInfo(context)
        if (info == null) {
            logger.w(
                this,
                "Cannot retrieve $notificationProvider AdvertisingIdClient.Info",
            )
        }
        val id = info?.id
        val isLimitAdTrackingEnabled = info?.isLimitAdTrackingEnabled ?: false
        return id to isLimitAdTrackingEnabled
    }

    override fun ensureVersionCompatibility(context: Context, logParent: Any) {
    }

    override fun isAvailable(context: Context) = HuaweiApiAvailability.getInstance()
        .isHuaweiMobileServicesAvailable(context) == ConnectionResult.SUCCESS

}