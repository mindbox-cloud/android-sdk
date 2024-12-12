package cloud.mindbox.mindbox_rustore

import android.app.Application
import android.content.Context
import android.os.Build
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import ru.rustore.sdk.core.util.RuStoreUtils
import ru.rustore.sdk.pushclient.RuStorePushClient
import ru.rustore.sdk.pushclient.common.logger.DefaultLogger
import ru.rustore.sdk.pushclient.messaging.model.RemoteMessage
import java.util.UUID
import kotlin.coroutines.resumeWithException

class RuStoreServiceHandler(
    private val logger: MindboxLogger,
    private val exceptionHandler: ExceptionHandler,
    private val projectId: String,
) : PushServiceHandler() {

    companion object {
        const val RU_STORE_MIN_API_VERSION = Build.VERSION_CODES.N
    }

    override val notificationProvider: String = MindboxRuStoreInternal.tag

    override suspend fun initService(context: Context) {
        exceptionHandler.runCatchingSuspending {
            withContext(Dispatchers.Main) {
                RuStorePushClient.init(
                    application = context.applicationContext as Application,
                    projectId = projectId,
                    logger = DefaultLogger(),
                )
            }
        }
    }

    override fun convertToRemoteMessage(message: Any): MindboxRemoteMessage? =
        message.takeIf { it is RemoteMessage }?.let {
            exceptionHandler.runCatching(null) {
                MindboxRuStore.convertToMindboxRemoteMessage(message as RemoteMessage)
            }
        }

    override fun getAdsId(context: Context): Pair<String?, Boolean> =
        UUID.randomUUID().toString() to false

    override fun ensureVersionCompatibility(context: Context, logParent: Any) {
        // do nothing
    }

    override fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < RU_STORE_MIN_API_VERSION) {
            logger.w(
                this,
                "RuStore push notifications do not work on this device. " +
                    "Requires at least Android API $RU_STORE_MIN_API_VERSION"
            )
            return false
        }

        return RuStoreUtils.isRuStoreInstalled(context)
    }

    override suspend fun getToken(context: Context): String? =
        suspendCancellableCoroutine { continuation ->
            RuStorePushClient.getToken()
                .addOnSuccessListener { token ->
                    continuation.resumeWith(Result.success(token))
                }
                .addOnFailureListener { throwable ->
                    continuation.resumeWithException(throwable)
                }
        }
}
