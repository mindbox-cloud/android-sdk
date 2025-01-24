package cloud.mindbox.mobile_sdk.pushes

import android.content.Context
import cloud.mindbox.mobile_sdk.logger.MindboxLog
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import cloud.mindbox.mobile_sdk.utils.loggingRunCatchingSuspending
import java.util.UUID

/**
* A class for internal sdk work only. Do not extend or use it
* */
abstract class PushServiceHandler : MindboxLog {

    companion object {
        private const val ZERO_ID = "00000000-0000-0000-0000-000000000000"
    }

    abstract val notificationProvider: String

    abstract suspend fun initService(context: Context)

    abstract fun convertToRemoteMessage(message: Any): MindboxRemoteMessage?

    fun getAdsIdentification(context: Context): String = LoggingExceptionHandler.runCatching(
        block = {
            val (id, isLimitAdTrackingEnabled) = getAdsId(context)

            if (isLimitAdTrackingEnabled || id.isNullOrEmpty() || id == ZERO_ID) {
                logI(
                    "Device uuid cannot be received from $notificationProvider AdvertisingIdClient. " +
                        "Will be generated from random. " +
                        "isLimitAdTrackingEnabled = $isLimitAdTrackingEnabled, " +
                        "uuid from AdvertisingIdClient = $id",
                )
                generateRandomUuid()
            } else {
                logI(
                    "Received from $notificationProvider AdvertisingIdClient: " +
                        "device uuid - $id",
                )
                id
            }
        },
        defaultValue = onAdsIdAcquisitionFailure(),
    )

    abstract fun getAdsId(context: Context): Pair<String?, Boolean>

    abstract fun ensureVersionCompatibility(context: Context, logParent: Any)

    fun isServiceAvailable(context: Context): Boolean = try {
        val isAvailable = isAvailable(context)
        if (!isAvailable) {
            MindboxLoggerImpl.w(this, "$notificationProvider services are not available")
        }
        isAvailable
    } catch (e: Exception) {
        logW(
            "Unable to determine $notificationProvider services availability. " +
                "Failed with exception $e",
        )
        false
    }

    suspend fun registerToken(
        context: Context,
        previousToken: String?,
    ): String? = loggingRunCatchingSuspending(null) {
        val token = getToken(context)
        if (!token.isNullOrEmpty() && token != previousToken) {
            logI("Token gets or updates from $notificationProvider")
        }
        token
    }

    protected abstract fun isAvailable(context: Context): Boolean

    protected abstract suspend fun getToken(context: Context): String?

    private fun onAdsIdAcquisitionFailure(): (Throwable) -> String = {
        logI(
            "Device uuid cannot be received from $notificationProvider AdvertisingIdClient. " +
                "Will be generated from random",
        )
        generateRandomUuid()
    }

    private fun generateRandomUuid() = UUID.randomUUID().toString()
}
