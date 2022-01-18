package cloud.mindbox.mobile_sdk_core.pushes

import android.content.Context
import cloud.mindbox.mobile_sdk_core.logger.MindboxLoggerInternal
import cloud.mindbox.mobile_sdk_core.managers.SharedPreferencesManager
import cloud.mindbox.mobile_sdk_core.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk_core.returnOnException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

abstract class PushServiceHandler {

    private val tokenCallbacks = ConcurrentHashMap<String, (String?) -> Unit>()

    abstract val notificationProvider: String

    abstract fun initService(context: Context)

    abstract fun getToken(context: Context): String?

    abstract fun getAdsIdentification(context: Context): String

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

    fun subscribeToken(subscription: (String?) -> Unit): String {
        val subscriptionId = UUID.randomUUID().toString()

        if (SharedPreferencesManager.isInitialized() && !MindboxPreferences.isFirstInitialize) {
            subscription.invoke(MindboxPreferences.pushToken)
        } else {
            tokenCallbacks[subscriptionId] = subscription
        }

        return subscriptionId
    }

    fun disposeTokenSubscription(subscriptionId: String) {
        tokenCallbacks.remove(subscriptionId)
    }

    fun getTokenSaveDate(): String = runCatching {
        return MindboxPreferences.tokenSaveDate
    }.returnOnException { "" }

    fun deliverToken(token: String?) {
        Executors.newSingleThreadScheduledExecutor().schedule({
            tokenCallbacks.keys.asIterable().forEach { key ->
                tokenCallbacks[key]?.invoke(token)
                tokenCallbacks.remove(key)
            }
        }, 1, TimeUnit.SECONDS)
    }

}