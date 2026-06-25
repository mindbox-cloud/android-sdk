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

    @Volatile
    private var cachedNamedAppName: String? = null

    override val notificationProvider: String = MindboxFirebase.tag

    override suspend fun initService(context: Context) {
        FirebaseApp.initializeApp(context)
        createNamedAppIfConfigured(context)
    }

    /**
     * If the host app configured a named FirebaseApp via the `mindbox_firebase_app_name`
     * string resource, makes sure it exists — creating it from the `[DEFAULT]` app's options (the same
     * Firebase project), or reusing one the client already created. This gives Mindbox
     * an FCM token isolated from `[DEFAULT]`, whose token third-party SDKs may churn.
     * Does nothing when no named app is configured.
     */
    private fun createNamedAppIfConfigured(context: Context) {
        val appName = namedFirebaseAppName(context)
        if (appName.isBlank()) return
        if (FirebaseApp.getApps(context).any { it.name == appName }) {
            logger.i(this, "Named FirebaseApp '$appName' already exists, reusing it")
            return
        }
        runCatching {
            FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, appName)
        }.fold(
            onSuccess = { logger.i(this, "Created isolated named FirebaseApp '$appName'") },
            onFailure = { error ->
                logger.e(this, "Failed to create named FirebaseApp '$appName'", error)
            },
        )
    }

    override suspend fun getToken(
        context: Context
    ): String? = suspendCancellableCoroutine { continuation ->
        resolveFirebaseMessaging(context).token
            .addOnCanceledListener {
                continuation.resumeWithException(CancellationException())
            }
            .addOnSuccessListener { token ->
                continuation.resumeWith(Result.success(token))
            }
            .addOnFailureListener(continuation::resumeWithException)
    }

    private fun resolveFirebaseMessaging(context: Context): FirebaseMessaging {
        val appName = namedFirebaseAppName(context)
        if (appName.isBlank()) {
            return FirebaseMessaging.getInstance()
        }
        return runCatching {
            FirebaseApp.getInstance(appName).get(FirebaseMessaging::class.java)
        }.getOrElse { error ->
            logger.e(
                this,
                "Could not resolve named FirebaseApp '$appName', " +
                    "falling back to [DEFAULT] FirebaseApp",
                error,
            )
            FirebaseMessaging.getInstance()
        }
    }

    private fun namedFirebaseAppName(context: Context): String =
        cachedNamedAppName ?: exceptionHandler.runCatching(defaultValue = "") {
            context.getString(R.string.mindbox_firebase_app_name)
        }.also { cachedNamedAppName = it }

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
