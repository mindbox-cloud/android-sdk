package cloud.mindbox.mindbox_rustore

import android.app.Application
import android.content.Context
import android.os.Build
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.rustore.sdk.core.util.RuStoreUtils
import ru.rustore.sdk.pushclient.RuStorePushClient
import ru.rustore.sdk.pushclient.common.logger.DefaultLogger
import java.util.UUID
import kotlin.coroutines.resumeWithException

class RuStoreServiceHandler(
    private val logger: MindboxLogger,
    private val exceptionHandler: ExceptionHandler,
    private val projectId: String,
) : PushServiceHandler() {

    companion object {
        const val RU_STORE_MIN_API_VERSION = Build.VERSION_CODES.M
    }

    override val notificationProvider: String = MindboxRuStoreInternal.tag

    override fun initService(context: Context) {
        exceptionHandler.runCatching {
            RuStorePushClient.init(
                application = context.applicationContext as Application,
                projectId = projectId,
                logger = DefaultLogger(),
            )
        }
    }

    override fun convertToRemoteMessage(message: Any): MindboxRemoteMessage? {
        TODO("Not yet implemented")
    }

    override fun getAdsId(context: Context): Pair<String?, Boolean> =
        UUID.randomUUID().toString() to false

    override fun ensureVersionCompatibility(context: Context, logParent: Any) {
        // do nothing
    }

    override fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < RU_STORE_MIN_API_VERSION) {
            logger.e(
                this, "RuStore push notifications do not work on this device. " +
                    "Requires at least Android API $RU_STORE_MIN_API_VERSION"
            )
            return false
        }

        return RuStoreUtils.isRuStoreInstalled(context)
    }

    override suspend fun getToken(context: Context): String? = suspendCancellableCoroutine { continuation ->
        RuStorePushClient.getToken()
            .addOnSuccessListener { token ->
                continuation.resumeWith(Result.success(token))
            }
            .addOnFailureListener { throwable ->
                continuation.resumeWithException(throwable)
            }
    }
}