package cloud.mindbox.mindbox_hms

import android.content.Context
import cloud.mindbox.mobile_sdk_core.pushes.PushServiceHandler
import com.huawei.agconnect.AGConnectOptionsBuilder
import com.huawei.hms.aaid.HmsInstanceId
import com.huawei.hms.push.HmsMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.*

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

    override fun getAdsIdentification(context: Context): String {
//        TODO("Not yet implemented")
        return UUID.randomUUID().toString()
    }

    override fun ensureVersionCompatibility(context: Context, logParent: Any) {
//        TODO("Not yet implemented")
    }

}