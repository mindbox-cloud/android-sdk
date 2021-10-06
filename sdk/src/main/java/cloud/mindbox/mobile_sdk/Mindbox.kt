package cloud.mindbox.mobile_sdk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.annotation.DrawableRes
import cloud.mindbox.mobile_sdk.logger.Level
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.managers.*
import cloud.mindbox.mobile_sdk.models.*
import cloud.mindbox.mobile_sdk.models.operation.request.OperationBodyRequestBase
import cloud.mindbox.mobile_sdk.models.operation.response.OperationResponse
import cloud.mindbox.mobile_sdk.models.operation.response.OperationResponseBase
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

    /**
     * Used for determination app open from push
     */
    const val IS_OPENED_FROM_PUSH_BUNDLE_KEY = "isOpenedFromPush"

    private const val OPERATION_NAME_REGEX = "^[A-Za-z0-9-\\.]{1,249}\$"

    private val mindboxJob = Job()
    private val mindboxScope = CoroutineScope(Default + mindboxJob)
    private val deviceUuidCallbacks = ConcurrentHashMap<String, (String) -> Unit>()
    private val fmsTokenCallbacks = ConcurrentHashMap<String, (String?) -> Unit>()
    private lateinit var lifecycleManager: LifecycleManager

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
    fun getFmsTokenSaveDate(): String = runCatching {
        return MindboxPreferences.firebaseTokenSaveDate
    }.returnOnException { "" }

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
     * Creates and deliveries event of "Push delivered". Recommended call this method from
     * background thread.
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
     * Creates and deliveries event of "Push clicked". Recommended call this method from background
     * thread.
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
     * Creates and deliveries event of "Push clicked".
     * Recommended to be used with Mindbox SDK pushes with [handleRemoteMessage] method.
     * Intent should contain "uniq_push_key" and "uniq_push_button_key" (optionally) in order to work correctly
     * Recommended call this method from background thread.
     *
     * @param context used to initialize the main tools
     * @param intent - intent recieved in app component
     *
     * @return true if Mindbox SDK recognises push intent as Mindbox SDK push intent
     *         false if Mindbox SDK cannot find critical information in intent
     */
    fun onPushClicked(context: Context, intent: Intent): Boolean = runCatching {
        PushNotificationManager.getUniqKeyFromPushIntent(intent)
            ?.let { uniqKey ->
                val pushButtonUniqKey = PushNotificationManager
                    .getUniqPushButtonKeyFromPushIntent(intent)
                onPushClicked(context, uniqKey, pushButtonUniqKey)
                true
            }
            ?: false
    }.returnOnException { false }


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
                    val isTrackVisitNotSent = Mindbox::lifecycleManager.isInitialized
                        && !lifecycleManager.isTrackVisitSent()
                    if (isTrackVisitNotSent) {
                        sendTrackVisitEvent(context, DIRECT)
                    }
                } else {
                    updateAppInfo(context)
                    MindboxEventManager.sendEventsIfExist(context)
                }
            }

            // Handle back app in foreground
            (context.applicationContext as? Application)?.apply {
                val applicationLifecycle = ProcessLifecycleOwner.get().lifecycle

                if (!Mindbox::lifecycleManager.isInitialized) {
                    val activity = context as? Activity
                    val isApplicationResumed = applicationLifecycle.currentState == RESUMED
                    if (isApplicationResumed && activity == null) {
                        MindboxLogger.e(
                            this@Mindbox,
                            "Incorrect context type for calling init in this place"
                        )
                    }

                    lifecycleManager = LifecycleManager(
                        currentActivityName = activity?.javaClass?.name,
                        currentIntent = activity?.intent,
                        isAppInBackground = !isApplicationResumed,
                        onTrackVisitReady = { source, requestUrl ->
                            runBlocking(Dispatchers.IO) {
                                sendTrackVisitEvent(context, source, requestUrl)
                            }
                        }
                    )
                } else {
                    unregisterActivityLifecycleCallbacks(lifecycleManager)
                    applicationLifecycle.removeObserver(lifecycleManager)
                    lifecycleManager.wasReinitialized()
                }

                registerActivityLifecycleCallbacks(lifecycleManager)
                applicationLifecycle.addObserver(lifecycleManager)
            }
        }.returnOnException { }
    }

    /**
     * Send track visit event after link or push was clicked for [Activity] with launchMode equals
     * "singleTop" or "singleTask" or if a client used the [Intent.FLAG_ACTIVITY_SINGLE_TOP] or
     * [Intent.FLAG_ACTIVITY_NEW_TASK]
     * flag when calling {@link #startActivity}.
     *
     * @param intent new intent for activity, which was received in [Activity.onNewIntent] method
     */
    fun onNewIntent(intent: Intent?) = runCatching {
        if (Mindbox::lifecycleManager.isInitialized) {
            lifecycleManager.onNewIntent(intent)
        }
    }.logOnException()

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
     * Creates and deliveries event with specified name and body. Recommended call this method from
     * background thread.
     *
     * @param context current context is used
     * @param operationSystemName the name of asynchronous operation
     * @param operationBody [T] which extends [OperationBody] and will be send as event json body of operation.
     */
    @Deprecated("Used Mindbox.executeAsyncOperation with OperationBodyRequestBase")
    fun <T : OperationBody> executeAsyncOperation(
        context: Context,
        operationSystemName: String,
        operationBody: T
    ) = asyncOperation(context, operationSystemName, operationBody)

    /**
     * Creates and deliveries event with specified name and body. Recommended call this method from
     * background thread.
     *
     * @param context current context is used
     * @param operationSystemName the name of asynchronous operation
     * @param operationBody [T] which extends [OperationBodyRequestBase] and will be send as event json body of operation.
     */
    fun <T : OperationBodyRequestBase> executeAsyncOperation(
        context: Context,
        operationSystemName: String,
        operationBody: T
    ) = asyncOperation(context, operationSystemName, operationBody)

    /**
     * Creates and deliveries event synchronously with specified name and body.
     *
     * @param context current context is used
     * @param operationSystemName the name of synchronous operation
     * @param operationBody [T] which extends [OperationBodyRequestBase] and will be send as event json body of operation.
     * @param onSuccess Callback for response typed [OperationResponse] that will be invoked for success response to a given request.
     * @param onError Callback for response typed [MindboxError] and will be invoked for error response to a given request.
     */
    fun <T : OperationBodyRequestBase> executeSyncOperation(
        context: Context,
        operationSystemName: String,
        operationBody: T,
        onSuccess: (OperationResponse) -> Unit,
        onError: (MindboxError) -> Unit
    ) = executeSyncOperation(
        context = context,
        operationSystemName = operationSystemName,
        operationBody = operationBody,
        classOfV = OperationResponse::class.java,
        onSuccess = onSuccess,
        onError = onError
    )

    /**
     * Creates and deliveries event synchronously with specified name and body.
     *
     * @param context current context is used
     * @param operationSystemName the name of synchronous operation
     * @param operationBody [T] which extends [OperationBodyRequestBase] and will be send as event json body of operation.
     * @param classOfV Class type for response object.
     * @param onSuccess Callback for response typed [V] which extends [OperationResponseBase] that will be invoked for success response to a given request.
     * @param onError Callback for response typed [MindboxError] and will be invoked for error response to a given request.
     */
    fun <T : OperationBodyRequestBase, V : OperationResponseBase> executeSyncOperation(
        context: Context,
        operationSystemName: String,
        operationBody: T,
        classOfV: Class<V>,
        onSuccess: (V) -> Unit,
        onError: (MindboxError) -> Unit
    ) {
        runCatching {
            if (operationSystemName.matches(OPERATION_NAME_REGEX.toRegex())) {
                mindboxScope.launch {
                    initComponents(context)
                    MindboxEventManager.syncOperation(
                        context = context,
                        name = operationSystemName,
                        body = operationBody,
                        classOfV = classOfV,
                        onSuccess = onSuccess,
                        onError = onError
                    )
                }
            } else {
                MindboxLogger.w(
                    this,
                    "Operation name is incorrect. It should contain only latin letters, number, '-' or '.' and length from 1 to 250."
                )
            }
        }
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
     * @param activities map (url mask) -> (Activity class). When clicked on push or button with url, corresponding activity will be opened
     *        Currently supports '*' character - indicator of zero or more numerical, alphabetic and punctuation characters
     *        e.g. mask "https://sample.com/" will match only "https://sample.com/" link
     *        whereas mask "https://sample.com/\u002A" will match
     *        "https://sample.com/", "https://sample.com/foo", "https://sample.com/foo/bar", "https://sample.com/foo?bar=baz" and other masks
     * @param defaultActivity default activity to be opened if url was not found in [activities]
     *
     * @return true if notification is Mindbox push and it's successfully handled, false otherwise.
     */
    fun handleRemoteMessage(
        context: Context,
        message: RemoteMessage?,
        channelId: String,
        channelName: String,
        @DrawableRes pushSmallIcon: Int,
        defaultActivity: Class<out Activity>,
        channelDescription: String? = null,
        activities: Map<String, Class<out Activity>>? = null
    ): Boolean = PushNotificationManager.handleRemoteMessage(
        context = context,
        remoteMessage = message,
        channelId = channelId,
        channelName = channelName,
        pushSmallIcon = pushSmallIcon,
        channelDescription = channelDescription,
        activities = activities,
        defaultActivity = defaultActivity
    )

    /**
     * Retrieves url from intent generated by notification manager
     *
     * @param intent an intent sent by SDK and received in BroadcastReceiver
     * @return url associated with the push intent or null if there is none
     */
    fun getUrlFromPushIntent(intent: Intent?): String? = intent?.let {
        PushNotificationManager.getUrlFromPushIntent(intent)
    }

    internal fun initComponents(context: Context) {
        SharedPreferencesManager.with(context)
        DbManager.init(context)
        FirebaseApp.initializeApp(context)
    }

    private fun <T> asyncOperation(
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
                externalDeviceUUID = configuration.previousDeviceUUID,
                isNotificationsEnabled = isNotificationEnabled,
                subscribe = configuration.subscribeCustomerIfCreated,
                instanceId = instanceId
            )

            MindboxEventManager.appInstalled(context, initData, configuration.shouldCreateCustomer)

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

            val firebaseToken = token
                ?: withContext(mindboxScope.coroutineContext) { IdentifierManager.registerFirebaseToken() }

            val isTokenAvailable = !firebaseToken.isNullOrEmpty()

            val isNotificationEnabled = IdentifierManager.isNotificationsEnabled(context)

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

    private fun sendTrackVisitEvent(
        context: Context,
        @TrackVisitSource source: String? = null,
        requestUrl: String? = null
    ) = runCatching {
        val applicationContext = context.applicationContext
        val endpointId = DbManager.getConfigurations()?.endpointId ?: return
        val trackVisitData = TrackVisitData(
            ianaTimeZone = TimeZone.getDefault().id,
            endpointId = endpointId,
            source = source,
            requestUrl = requestUrl
        )

        MindboxEventManager.appStarted(applicationContext, trackVisitData)
    }.logOnException()

    private fun deliverDeviceUuid(deviceUuid: String) {
        Executors.newSingleThreadScheduledExecutor().schedule({
            deviceUuidCallbacks.keys.asIterable().forEach { key ->
                deviceUuidCallbacks[key]?.invoke(deviceUuid)
                deviceUuidCallbacks.remove(key)
            }
        }, 1, TimeUnit.SECONDS)
    }

    private fun deliverFmsToken(token: String?) {
        Executors.newSingleThreadScheduledExecutor().schedule({
            fmsTokenCallbacks.keys.asIterable().forEach { key ->
                fmsTokenCallbacks[key]?.invoke(token)
                fmsTokenCallbacks.remove(key)
            }
        }, 1, TimeUnit.SECONDS)
    }

}
