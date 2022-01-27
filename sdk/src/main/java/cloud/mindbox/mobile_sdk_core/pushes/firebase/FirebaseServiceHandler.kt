package cloud.mindbox.mobile_sdk_core.pushes.firebase

import android.content.Context
import android.os.Build
import cloud.mindbox.mobile_sdk_core.logger.MindboxLoggerInternal
import cloud.mindbox.mobile_sdk_core.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk_core.returnOnException
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import com.google.android.gms.common.GoogleApiAvailability

object FirebaseServiceHandler : PushServiceHandler() {

    override val notificationProvider: String = "FCM"

    override fun initService(context: Context) {
        FirebaseApp.initializeApp(context)
    }

    override suspend fun getToken(
        scope: CoroutineScope,
        context: Context
    ): String? = suspendCoroutine { continuation ->
        FirebaseMessaging.getInstance().token
            .addOnCanceledListener {
                continuation.resumeWithException(CancellationException())
            }
            .addOnSuccessListener { token ->
                continuation.resumeWith(Result.success(token))
            }
            .addOnFailureListener(continuation::resumeWithException)
    }

    override fun getAdsId(context: Context): Pair<String?, Boolean> {
        val advertisingIdInfo = AdvertisingIdClient.getAdvertisingIdInfo(context)
        val id = advertisingIdInfo.id
        val isLimitAdTrackingEnabled = advertisingIdInfo.isLimitAdTrackingEnabled
        return id to isLimitAdTrackingEnabled
    }

    override fun ensureVersionCompatibility(context: Context, logParent: Any) {
        // Handle SSL error for Android less 21
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            try {
                ProviderInstaller.installIfNeeded(context)
            } catch (repairableException: GooglePlayServicesRepairableException) {
                MindboxLoggerInternal.e(
                    logParent,
                    "GooglePlayServices should be updated",
                    repairableException
                )
            } catch (notAvailableException: GooglePlayServicesNotAvailableException) {
                MindboxLoggerInternal.e(
                    logParent,
                    "GooglePlayServices aren't available",
                    notAvailableException
                )
            }
        }
    }

    override fun isAvailable(context: Context) = GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

}