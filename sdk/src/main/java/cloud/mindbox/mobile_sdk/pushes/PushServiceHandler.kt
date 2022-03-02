package cloud.mindbox.mobile_sdk.pushes

import android.content.Context
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import java.util.*

abstract class PushServiceHandler {

    companion object {

        private const val ZERO_ID = "00000000-0000-0000-0000-000000000000"

    }

    abstract val notificationProvider: String

    abstract fun initService(context: Context)

    abstract fun convertToRemoteMessage(message: Any): RemoteMessage?

    fun getAdsIdentification(context: Context): String = LoggingExceptionHandler.runCatching(
        block = {
            val (id, isLimitAdTrackingEnabled) = getAdsId(context)

            if (isLimitAdTrackingEnabled || id.isNullOrEmpty() || id == ZERO_ID) {
                MindboxLoggerImpl.d(
                    this,
                    "Device uuid cannot be received from $notificationProvider AdvertisingIdClient. " +
                            "Will be generated from random. " +
                            "isLimitAdTrackingEnabled = $isLimitAdTrackingEnabled, " +
                            "uuid from AdvertisingIdClient = $id",
                )
                generateRandomUuid()
            } else {
                MindboxLoggerImpl.d(
                    this,
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
        MindboxLoggerImpl.w(
            this,
            "Unable to determine $notificationProvider services availability. " +
                    "Failed with exception $e",
        )
        false
    }

    suspend fun registerToken(
        context: Context,
        previousToken: String?,
    ): String? = try {
        val token = getToken(context)
        if (!token.isNullOrEmpty() && token != previousToken) {
            MindboxLoggerImpl.i(this, "Token gets or updates from $notificationProvider")
        }
        token
    } catch (e: Exception) {
        MindboxLoggerImpl.w(
            this,
            "Fetching $notificationProvider registration token failed with exception $e",
        )
        null
    }

    protected abstract fun isAvailable(context: Context): Boolean

    protected abstract suspend fun getToken(context: Context): String?

    private fun onAdsIdAcquisitionFailure(): (Throwable) -> String = {
        MindboxLoggerImpl.d(
            this,
            "Device uuid cannot be received from $notificationProvider AdvertisingIdClient. " +
                    "Will be generated from random",
        )
        generateRandomUuid()
    }

    private fun generateRandomUuid() = UUID.randomUUID().toString()

}