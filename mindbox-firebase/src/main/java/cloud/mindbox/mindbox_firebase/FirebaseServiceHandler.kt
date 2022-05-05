package cloud.mindbox.mindbox_firebase

import android.content.Context
import android.os.Build
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.resumeWithException
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.suspendCancellableCoroutine

internal class FirebaseServiceHandler(
    private val logger: MindboxLogger,
    exceptionHandler: ExceptionHandler,
) : PushServiceHandler() {

    private val messageTransformer = FirebaseRemoteMessageTransformer(exceptionHandler)

    override val notificationProvider: String = "FCM"

    override fun initService(context: Context) {
        FirebaseApp.initializeApp(context)
    }

    override suspend fun getToken(
        context: Context,
    ): String? = suspendCancellableCoroutine { continuation ->
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
                logger.e(
                    logParent,
                    "GooglePlayServices should be updated",
                    repairableException,
                )
            } catch (notAvailableException: GooglePlayServicesNotAvailableException) {
                logger.e(
                    logParent,
                    "GooglePlayServices aren't available",
                    notAvailableException,
                )
            }
        }
    }

    override fun checkServiceAvailable(context: Context) = GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    override fun convertToRemoteMessage(message: Any) = if (message is RemoteMessage) {
        messageTransformer.transform(message)
    } else {
        null
    }

}