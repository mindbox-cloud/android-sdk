package cloud.mindbox.mobile_sdk_core.pushes

import android.content.Context
import cloud.mindbox.mobile_sdk_core.logger.MindboxLoggerInternal
import kotlinx.coroutines.CoroutineScope

abstract class PushServiceHandler {

    abstract val notificationProvider: String

    abstract fun initService(context: Context)

    abstract fun getAdsIdentification(context: Context): String?

    abstract fun ensureVersionCompatibility(context: Context, logParent: Any)

    fun isServiceAvailable(context: Context): Boolean = try {
        val isAvailable = isAvailable(context)
        if (!isAvailable) {
            MindboxLoggerInternal.w(this, "$notificationProvider services are not available")
        }
        isAvailable
    } catch (e: Exception) {
        MindboxLoggerInternal.e(this, "Unable to determine $notificationProvider services availability. Failed with exception $e")
        false
    }

    protected abstract fun isAvailable(context: Context): Boolean

    protected abstract suspend fun getToken(scope: CoroutineScope, context: Context): String?

    suspend fun registerToken(
        scope: CoroutineScope,
        context: Context,
        previousToken: String?,
    ): String? = try {
        val token = getToken(scope, context)
        if (!token.isNullOrEmpty() && token != previousToken) {
            MindboxLoggerInternal.i(this, "Token gets or updates from $notificationProvider")
        }
        token
    } catch (e: Exception) {
        MindboxLoggerInternal.e(this, "Fetching $notificationProvider registration token failed with exception $e")
        null
    }







}