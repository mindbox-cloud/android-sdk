package cloud.mindbox.mindbox_firebase

import android.content.Context
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

internal class FirebaseServiceHandler(
    private val logger: MindboxLogger,
    private val exceptionHandler: ExceptionHandler,
) : PushServiceHandler() {

    override val notificationProvider: String = MindboxFirebase.tag

    override suspend fun initService(context: Context) {
        FirebaseApp.initializeApp(context)
    }

    override suspend fun getToken(
        context: Context
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

    override fun isAvailable(context: Context) = GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    override fun convertToRemoteMessage(message: Any) = if (message is RemoteMessage) {
        exceptionHandler.runCatching(null) {
            MindboxFirebase.convertToMindboxRemoteMessage(message)
        }
    } else {
        null
    }
}
