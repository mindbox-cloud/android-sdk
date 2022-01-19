package cloud.mindbox.mobile_sdk_core.pushes

import android.content.Context
import cloud.mindbox.mobile_sdk_core.logger.MindboxLoggerInternal

abstract class PushServiceHandler {

    abstract val notificationProvider: String

    abstract fun initService(context: Context)

    abstract fun getToken(context: Context): String?

    abstract fun getAdsIdentification(context: Context): String?

    abstract fun ensureVersionCompatibility(context: Context, logParent: Any)

    fun registerToken(context: Context, previousToken: String?): String? = try {
        val token = getToken(context)
        if (!token.isNullOrEmpty() && token != previousToken) {
            MindboxLoggerInternal.i(this, "Token gets or updates from $notificationProvider")
        }
        token
    } catch (e: Exception) {
        MindboxLoggerInternal.w(this, "Fetching $notificationProvider registration token failed with exception $e")
        null
    }







}