package cloud.mindbox.mindbox_hms

import android.content.Context
import cloud.mindbox.mobile_sdk_core.logger.MindboxLoggerInternal
import cloud.mindbox.mobile_sdk_core.pushes.PushServiceHandler
import com.huawei.agconnect.AGConnectOptionsBuilder
import com.huawei.hms.aaid.HmsInstanceId
import com.huawei.hms.api.ConnectionResult
import com.huawei.hms.api.HuaweiApiAvailability
import com.huawei.hms.push.HmsMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.huawei.hms.ads.identifier.AdvertisingIdClient
import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object HuaweiServiceHandler : PushServiceHandler() {

    private const val HMS_APP_ID_KEY = "client/app_id"
    private const val HMS_TOKEN_SCOPE = "HCM"
    private const val TOKEN_ACQUISITION_DELAY = 2000L

    override val notificationProvider: String = "HMS"

    override fun initService(context: Context) {
        HmsMessaging.getInstance(context).isAutoInitEnabled = true
    }

    override suspend fun getToken(
        scope: CoroutineScope,
        context: Context,
    ): String? = withContext(scope.coroutineContext) {
        val appId = AGConnectOptionsBuilder().build(context).getString(HMS_APP_ID_KEY)
        val hms = HmsInstanceId.getInstance(context)
        hms.getToken(appId, HMS_TOKEN_SCOPE)?.takeIf(String::isNotEmpty) ?: run {
            delay(TOKEN_ACQUISITION_DELAY)
            hms.getToken(appId, HMS_TOKEN_SCOPE)
        }
    }

    override suspend fun getAdsId(context: Context): Pair<String?, Boolean> = suspendCoroutine { continuation ->
        try {
            val info: AdvertisingIdClient.Info? = AdvertisingIdClient.getAdvertisingIdInfo(context)
            if (info == null) {
                MindboxLoggerInternal.w(this, "Cannot retrieve $notificationProvider AdvertisingIdClient.Info")
            }
            val id = info?.id
            val isLimitAdTrackingEnabled = info?.isLimitAdTrackingEnabled ?: false
            continuation.resumeWith(Result.success(id to isLimitAdTrackingEnabled))
        } catch (e: IOException) {
            continuation.resumeWithException(e)
        }

    }

    override fun ensureVersionCompatibility(context: Context, logParent: Any) {
//        TODO("Not yet implemented")
    }

    override fun isAvailable(context: Context) = HuaweiApiAvailability.getInstance()
        .isHuaweiMobileServicesAvailable(context) == ConnectionResult.SUCCESS

}