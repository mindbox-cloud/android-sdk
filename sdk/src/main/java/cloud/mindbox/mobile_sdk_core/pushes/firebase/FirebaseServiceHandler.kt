package cloud.mindbox.mobile_sdk_core.pushes.firebase

import android.content.Context
import android.os.Build
import cloud.mindbox.mobile_sdk_core.MindboxInternalCore
import cloud.mindbox.mobile_sdk_core.logOnException
import cloud.mindbox.mobile_sdk_core.logger.MindboxLogger
import cloud.mindbox.mobile_sdk_core.managers.SharedPreferencesManager
import cloud.mindbox.mobile_sdk_core.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk_core.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk_core.returnOnException
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object FirebaseServiceHandler : PushServiceHandler {

    private val fmsTokenCallbacks = ConcurrentHashMap<String, (String?) -> Unit>()

    private const val ZERO_ID = "00000000-0000-0000-0000-000000000000"

    override fun initService(context: Context) {
        FirebaseApp.initializeApp(context)
    }

    override fun subscribeToken(subscription: (String?) -> Unit): String {
        val subscriptionId = UUID.randomUUID().toString()

        if (SharedPreferencesManager.isInitialized() && !MindboxPreferences.isFirstInitialize) {
            subscription.invoke(MindboxPreferences.pushToken)
        } else {
            fmsTokenCallbacks[subscriptionId] = subscription
        }

        return subscriptionId
    }

    override fun disposeTokenSubscription(subscriptionId: String) {
        fmsTokenCallbacks.remove(subscriptionId)
    }

    override fun getTokenSaveDate(): String = runCatching {
        return MindboxPreferences.firebaseTokenSaveDate
    }.returnOnException { "" }

    override fun updateToken(context: Context, token: String) {
        runCatching {
            if (token.trim().isNotEmpty()) {
                MindboxInternalCore.initComponents(context)

                if (!MindboxPreferences.isFirstInitialize) {
                    MindboxInternalCore.mindboxScope.launch {
                        MindboxInternalCore.updateAppInfo(context, token)
                    }
                }
            }
        }.logOnException()
    }

    override fun deliverToken(token: String?) {
        Executors.newSingleThreadScheduledExecutor().schedule({
            fmsTokenCallbacks.keys.asIterable().forEach { key ->
                fmsTokenCallbacks[key]?.invoke(token)
                fmsTokenCallbacks.remove(key)
            }
        }, 1, TimeUnit.SECONDS)
    }

    override fun registerToken(): String? = try {
        val token: String? = Tasks.await(FirebaseMessaging.getInstance().token)
        if (!token.isNullOrEmpty() && token != MindboxPreferences.pushToken) {
            MindboxLogger.i(this, "Token gets or updates from firebase")
        }
        token
    } catch (e: Exception) {
        MindboxLogger.w(this, "Fetching FCM registration token failed with exception $e")
        null
    }

    override fun getAdsIdentification(context: Context): String = runCatching {
        val advertisingIdInfo = AdvertisingIdClient.getAdvertisingIdInfo(context)
        val id = advertisingIdInfo.id
        if (advertisingIdInfo.isLimitAdTrackingEnabled || id.isNullOrEmpty() || id == ZERO_ID) {
            MindboxLogger.d(
                this,
                "Device uuid cannot be received from AdvertisingIdClient. Will be generated from random. " +
                        "isLimitAdTrackingEnabled=${advertisingIdInfo.isLimitAdTrackingEnabled}, " +
                        "uuid from AdvertisingIdClient = $id"
            )
            generateRandomUuid()
        } else {
            MindboxLogger.d(
                this, "Received from AdvertisingIdClient: device uuid - $id"
            )
            id
        }
    }.returnOnException {
        MindboxLogger.d(
            this,
            "Device uuid cannot be received from AdvertisingIdClient. Will be generated from random"
        )
        generateRandomUuid()
    }

    override fun ensureVersionCompatibility(context: Context, logParent: Any) {
        // Handle SSL error for Android less 21
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            try {
                ProviderInstaller.installIfNeeded(context)
            } catch (repairableException: GooglePlayServicesRepairableException) {
                MindboxLogger.e(
                    logParent,
                    "GooglePlayServices should be updated",
                    repairableException
                )
            } catch (notAvailableException: GooglePlayServicesNotAvailableException) {
                MindboxLogger.e(
                    logParent,
                    "GooglePlayServices aren't available",
                    notAvailableException
                )
            }
        }
    }

    private fun generateRandomUuid() = UUID.randomUUID().toString()

}