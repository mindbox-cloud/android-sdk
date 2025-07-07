package cloud.mindbox.mindbox_rustore

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.rustore.sdk.core.util.RuStoreUtils
import ru.rustore.sdk.pushclient.RuStorePushClient
import ru.rustore.sdk.pushclient.common.logger.DefaultLogger
import ru.rustore.sdk.pushclient.messaging.model.RemoteMessage
import java.util.UUID
import kotlin.coroutines.resumeWithException

internal class RuStoreServiceHandler(
    private val logger: MindboxLogger,
    private val exceptionHandler: ExceptionHandler,
    private val projectId: String,
) : PushServiceHandler() {

    companion object {
        const val RU_STORE_MIN_API_VERSION = Build.VERSION_CODES.N
        const val RU_STORE_META_DATA = "ru.rustore.sdk.pushclient.project_id"
    }

    override val notificationProvider: String = MindboxRuStore.tag

    override suspend fun initService(context: Context) {
        exceptionHandler.runCatchingSuspending {
            logger.d(this, "RuStoreServiceHandler initService")
            RuStorePushClient.init(
                application = context.applicationContext as Application,
                projectId = projectId.takeIf { it.isNotBlank() }
                    ?: runCatching {
                        context.packageManager
                            .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                            .metaData?.getString(RU_STORE_META_DATA) ?: ""
                    }.getOrDefault(""),
                logger = DefaultLogger(),
            )
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
