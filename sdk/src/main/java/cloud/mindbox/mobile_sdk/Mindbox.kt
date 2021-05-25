package cloud.mindbox.mobile_sdk

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.annotation.DrawableRes
import cloud.mindbox.mobile_sdk.logger.Level
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.managers.*
import cloud.mindbox.mobile_sdk.models.*
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object Mindbox {

    private const val OPERATION_NAME_REGEX = "^[A-Za-z0-9-\\.]{1,249}\$"

    private val mindboxJob = Job()
    private val mindboxScope = CoroutineScope(Default + mindboxJob)
    private val deviceUuidCallbacks = ConcurrentHashMap<String, (String) -> Unit>()
    private val fmsTokenCallbacks = ConcurrentHashMap<String, (String?) -> Unit>()

    /**
     * Subscribe to gets token of Firebase Messaging Service used by SDK
     *
     * @param subscription - invocation function with FMS token
     * @return String identifier of subscription
     * @see disposeFmsTokenSubscription
     */
    fun subscribeFmsToken(subscription: (String?) -> Unit): String {
        val subscriptionId = UUID.randomUUID().toString()

        if (SharedPreferencesManager.isInitialized() && !MindboxPreferences.isFirstInitialize) {
            subscription.invoke(MindboxPreferences.firebaseToken)
        } else {
            fmsTokenCallbacks[subscriptionId] = subscription
        }

        return subscriptionId
    }

    /**
     * Removes FMS token subscription if it is no longer necessary
     *
     * @param subscriptionId - identifier of the subscription to remove
     */
    fun disposeFmsTokenSubscription(subscriptionId: String) {
        fmsTokenCallbacks.remove(subscriptionId)
    }

    /**
     * Returns date of FMS token saving
     */
    fun getFmsTokenSaveDate(): String =
        runCatching { return MindboxPreferences.firebaseTokenSaveDate }
            .returnOnException { "" }

    /**
     * Returns SDK version
     */
    fun getSdkVersion(): String = runCatching { return BuildConfig.VERSION_NAME }
        .returnOnException { "" }

    /**
     * Subscribe to gets deviceUUID used by SDK
     *
     * @param subscription - invocation function with deviceUUID
     * @return String identifier of subscription
     * @see disposeDeviceUuidSubscription
     */
    fun subscribeDeviceUuid(subscription: (String) -> Unit): String {
        val subscriptionId = UUID.randomUUID().toString()

        if (SharedPreferencesManager.isInitialized() && !MindboxPreferences.isFirstInitialize) {
            subscription.invoke(MindboxPreferences.deviceUuid)
        } else {
            deviceUuidCallbacks[subscriptionId] = subscription
        }

        return subscriptionId
    }

    /**
     * Removes deviceUuid subscription if it is no longer necessary
     *
     * @param subscriptionId - identifier of the subscription to remove
     */
    fun disposeDeviceUuidSubscription(subscriptionId: String) {
        deviceUuidCallbacks.remove(subscriptionId)
    }

    /**
     * Updates FMS token for SDK
     * Call it from onNewToken on messaging service
     *
     * @param context used to initialize the main tools
     * @param token - token of FMS
     */
    fun updateFmsToken(context: Context, token: String) {
        runCatching {
            if (token.trim().isNotEmpty()) {
                initComponents(context)

                if (!MindboxPreferences.isFirstInitialize) {
                    mindboxScope.launch {
                        updateAppInfo(context, token)
                    }
                }
            }
        }.logOnException()
    }

    /**
     * Creates and deliveries event of "Push delivered"
     *
     * @param context used to initialize the main tools
     * @param uniqKey - unique identifier of push notification
     */
    fun onPushReceived(context: Context, uniqKey: String) {
        runCatching {
            initComponents(context)
            MindboxEventManager.pushDelivered(context, uniqKey)

            if (!MindboxPreferences.isFirstInitialize) {
                mindboxScope.launch {
                    updateAppInfo(context)
                }
            }
        }.logOnException()
    }

    /**
     * Creates and deliveries event of "Push clicked"
     *
     * @param context used to initialize the main tools
     * @param uniqKey - unique identifier of push notification
     * @param buttonUniqKey - unique identifier of push notification button
     */
    fun onPushClicked(context: Context, uniqKey: String, buttonUniqKey: String?) {
        runCatching {
            initComponents(context)
            MindboxEventManager.pushClicked(context, TrackClickData(uniqKey, buttonUniqKey))

            if (!MindboxPreferences.isFirstInitialize) {
                mindboxScope.launch {
                    updateAppInfo(context)
                }
            }
        }.logOnException()
    }

    /**
     * Initializes the SDK for further work.
     * We recommend calling it in onCreate on an application class
     *
     * @param context used to initialize the main tools
     * @param configuration contains the data that is needed to connect to the Mindbox
     */
    fun init(
        context: Context,
        configuration: MindboxConfiguration
    ) {
        runCatching {
            initComponents(context)

            val validationErrors =
                ValidationError()
                    .apply {
                        validateFields(
                            configuration.domain,
                            configuration.endpointId,
                            configuration.previousDeviceUUID,
                            configuration.previousInstallationId
                        )
                    }

            validationErrors.messages
                ?: throw InitializeMindboxException(validationErrors.messages.toString())

            mindboxScope.launch {
                if (MindboxPreferences.isFirstInitialize) {
                    firstInitialization(context, configuration)
                } else {
                    updateAppInfo(context)
                    MindboxEventManager.sendEventsIfExist(context)
                }
                sendTrackVisitEvent(context, configuration.endpointId)

                // Handle back app in foreground
                val lifecycleManager = LifecycleManager {
                    runBlocking(Dispatchers.IO) {
                        sendTrackVisitEvent(context, configuration.endpointId)
                    }
                }
                (context.applicationContext as? Application)
                    ?.registerActivityLifecycleCallbacks(lifecycleManager)
            }
        }.returnOnException { }
    }

    /**
     * Specifies log level for Mindbox
     *
     * @param level - is used for showing Mindbox logs starts from [Level]. Default
     * is [Level.INFO]. [Level.NONE] turns off all logs.
     */
    fun setLogLevel(level: Level) {
        MindboxLogger.level = level
    }

    /**
     * Creates and deliveries event with specified name and body.
     *
     * @param context current context is used
     * @param operationSystemName the name of asynchronous operation
     * @param operationBody [T] which extends [OperationBody] and will be send as event json body of operation.
     */
    fun <T : OperationBody> executeAsyncOperation(
        context: Context,
        operationSystemName: String,
        operationBody: T
    ) {
        runCatching {
            if (operationSystemName.matches(OPERATION_NAME_REGEX.toRegex())) {
                initComponents(context)
                MindboxEventManager.asyncOperation(context, operationSystemName, operationBody)
            } else {
                MindboxLogger.w(
                    this,
                    "Operation name is incorrect. It should contain only latin letters, number, '-' or '.' and length from 1 to 250."
                )
            }
        }.logOnException()
    }

    /**
     * Handles only Mindbox notification message from [FirebaseMessagingService].
     *
     * @param context context used for Mindbox initializing and push notification showing
     * @param message the [RemoteMessage] received from Firebase
     * @param channelId the id of channel for Mindbox pushes
     * @param channelName the name of channel for Mindbox pushes
     * @param pushSmallIcon icon for push notification as drawable resource
     * @param channelDescription the description of channel for Mindbox pushes. Default is null
     *
     * @return true if notification is Mindbox push and it's successfully handled, false otherwise.
     */
    fun handleRemoteMessage(
        context: Context,
        message: RemoteMessage?,
        channelId: String,
        channelName: String,
        @DrawableRes pushSmallIcon: Int,
        channelDescription: String? = null,
        delay: Long
    ): Boolean = PushNotificationManager.handleRemoteMessage(
        context = context,
        remoteMessage = message,
        channelId = channelId,
        channelName = channelName,
        pushSmallIcon = pushSmallIcon,
        channelDescription = channelDescription,
        delay = delay
    )

    internal fun initComponents(context: Context) {
        SharedPreferencesManager.with(context)
        DbManager.init(context)
        FirebaseApp.initializeApp(context)
    }

    private suspend fun initDeviceId(context: Context): String {
        val adid = mindboxScope.async { IdentifierManager.getAdsIdentification(context) }
        return adid.await()
    }

    private suspend fun firstInitialization(context: Context, configuration: MindboxConfiguration) {
        runCatching {
            val firebaseToken = withContext(mindboxScope.coroutineContext) {
                IdentifierManager.registerFirebaseToken()
            }

            val isNotificationEnabled = IdentifierManager.isNotificationsEnabled(context)
            val deviceUuid = initDeviceId(context)
            val instanceId = IdentifierManager.generateRandomUuid()

            DbManager.saveConfigurations(Configuration(configuration))

            val isTokenAvailable = !firebaseToken.isNullOrEmpty()
            val initData = InitData(
                token = firebaseToken ?: "",
                isTokenAvailable = isTokenAvailable,
                installationId = configuration.previousInstallationId,
                lastDeviceUuid = configuration.previousDeviceUUID,
                isNotificationsEnabled = isNotificationEnabled,
                subscribe = configuration.subscribeCustomerIfCreated,
                instanceId = instanceId
            )

            MindboxEventManager.appInstalled(context, initData)

            MindboxPreferences.deviceUuid = deviceUuid
            MindboxPreferences.firebaseToken = firebaseToken
            MindboxPreferences.isNotificationEnabled = isNotificationEnabled
            MindboxPreferences.instanceId = instanceId
            MindboxPreferences.isFirstInitialize = false

            deliverDeviceUuid(deviceUuid)
            deliverFmsToken(firebaseToken)
        }.logOnException()
    }

    private suspend fun updateAppInfo(context: Context, token: String? = null) {
        runCatching {
            Log.d("_____1", "start")
            val firebaseToken = token
                ?: withContext(mindboxScope.coroutineContext) { IdentifierManager.registerFirebaseToken() }

            val isTokenAvailable = !firebaseToken.isNullOrEmpty()

            val isNotificationEnabled = IdentifierManager.isNotificationsEnabled(context)

            Log.d("_____1", "before checking $firebaseToken")
            if ((isTokenAvailable && firebaseToken != MindboxPreferences.firebaseToken) || isNotificationEnabled != MindboxPreferences.isNotificationEnabled) {

                val initData = UpdateData(
                    token = firebaseToken ?: MindboxPreferences.firebaseToken ?: "",
                    isTokenAvailable = isTokenAvailable,
                    isNotificationsEnabled = isNotificationEnabled,
                    instanceId = MindboxPreferences.instanceId,
                    version = MindboxPreferences.infoUpdatedVersion
                )

                MindboxEventManager.appInfoUpdate(context, initData)

                MindboxPreferences.isNotificationEnabled = isNotificationEnabled
                MindboxPreferences.firebaseToken = firebaseToken
            }
        }.logOnException()
    }

    private fun sendTrackVisitEvent(context: Context, endpointId: String) {
        val trackVisitData = TrackVisitData(
            ianaTimeZone = TimeZone.getDefault().id,
            endpointId = endpointId
        )

        MindboxEventManager.appStarted(context, trackVisitData)
    }

    private fun deliverDeviceUuid(deviceUuid: String) {
        Executors.newSingleThreadScheduledExecutor().schedule({
            deviceUuidCallbacks.keys.forEach { key ->
                deviceUuidCallbacks[key]?.invoke(deviceUuid)
                deviceUuidCallbacks.remove(key)
            }
        }, 1, TimeUnit.SECONDS)
    }

    private fun deliverFmsToken(token: String?) {
        Executors.newSingleThreadScheduledExecutor().schedule({
            fmsTokenCallbacks.keys.forEach { key ->
                fmsTokenCallbacks[key]?.invoke(token)
                fmsTokenCallbacks.remove(key)
            }
        }, 1, TimeUnit.SECONDS)
    }
}