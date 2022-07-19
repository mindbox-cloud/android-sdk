package cloud.mindbox.mobile_sdk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.WorkerFactory
import cloud.mindbox.mobile_sdk.logger.Level
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.managers.*
import cloud.mindbox.mobile_sdk.models.*
import cloud.mindbox.mobile_sdk.models.operation.OperationBody
import cloud.mindbox.mobile_sdk.models.operation.request.OperationBodyRequestBase
import cloud.mindbox.mobile_sdk.models.operation.response.OperationResponse
import cloud.mindbox.mobile_sdk.models.operation.response.OperationResponseBase
import cloud.mindbox.mobile_sdk.pushes.*
import cloud.mindbox.mobile_sdk.pushes.handler.MindboxMessageHandler
import cloud.mindbox.mobile_sdk.pushes.handler.image.MindboxImageFailureHandler
import cloud.mindbox.mobile_sdk.pushes.handler.image.MindboxImageLoader
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
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

    /**
     * Factory for custom initialisation of WorkManager
     *
     * You don't need this if you are using default WorkManager initialisation
     *
     * If you disabled automatic initialisation, add this factory to your DelegatingWorkerFactory
     * in place, where you register your factories
     *
     * Example:
     *
     * override fun getWorkManagerConfiguration() = Configuration.Builder()
     *     .setWorkerFactory(
     *         DelegatingWorkerFactory().apply {
     *             // your factories
     *             addFactory(Mindbox.mindboxWorkerFactory) // Mindbox factory
     *         }
     *      )
     *     .build()
     */
    val mindboxWorkerFactory: WorkerFactory by lazy { MindboxWorkerFactory }

    private const val OPERATION_NAME_REGEX = "^[A-Za-z0-9-\\.]{1,249}\$"
    private const val DELIVER_TOKEN_DELAY = 1L

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        MindboxLoggerImpl.e(Mindbox, "Mindbox caught unhandled error", throwable)
    }
    private val infoUpdatedThreadDispatcher = Executors.newSingleThreadExecutor()
        .asCoroutineDispatcher()
    private val initScope = createMindboxScope()
    internal var mindboxScope = createMindboxScope()
        private set

    private val tokenCallbacks = ConcurrentHashMap<String, (String?) -> Unit>()
    private val deviceUuidCallbacks = ConcurrentHashMap<String, (String) -> Unit>()

    private lateinit var lifecycleManager: LifecycleManager

    internal var pushServiceHandler: PushServiceHandler? = null

    /**
     * Allows you to specify additional components for message handling
     * when calling the [handleRemoteMessage] function.
     *
     * Standard image failure handling strategies:
     *  - applyDefaultStrategy (Used by default)
     *  - applyDefaultAndRetryStrategy
     *  - retryOrCancelStrategy
     *  - retryOrDefaultStrategy
     *  - cancellationStrategy
     * See [MindboxImageFailureHandler] for more information.
     *
     *
     * Example:
     *
     *  class App : Application {
     *
     *      override fun onCreate() {
     *          ...
     *          val defaultImage = ContextCompat.getDrawable(this, R.drawable.ic_placeholder)?.toBitmap()
     *          Mindbox.setMessageHandling(
     *              imageLoader = MindboxImageLoader.default(),
     *              imageFailureHandler = MindboxImageFailureHandler.applyDefaultAndRetryStrategy(
     *                  maxAttempts = 5,
     *                  delay = 3_000,
     *                  defaultImage = defaultImage,
     *              )
     *          )
     *          ...
     *      }
     *  }
     *
     * @see MindboxImageLoader
     * @see MindboxImageFailureHandler
     * @see handleRemoteMessage
     */
    fun setMessageHandling(
        imageFailureHandler: MindboxImageFailureHandler = PushNotificationManager.messageHandler.imageFailureHandler,
        imageLoader: MindboxImageLoader = PushNotificationManager.messageHandler.imageLoader,
    ) {
        PushNotificationManager.messageHandler = MindboxMessageHandler(
            imageFailureHandler = imageFailureHandler,
            imageLoader = imageLoader,
        )
    }

    /**
     * Subscribe to gets token from push service used by SDK
     *
     * @param subscription - invocation function with push token
     * @return String identifier of subscription
     * @see disposePushTokenSubscription
     */
    @Deprecated(
        message = "Use subscribePushToken instead",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("subscribePushToken"),
    )
    fun subscribeFmsToken(subscription: (String?) -> Unit) = subscribePushToken(subscription)

    /**
     * Subscribe to gets token from push service used by SDK
     *
     * @param subscription - invocation function with push token
     * @return String identifier of subscription
     * @see disposePushTokenSubscription
     */
    fun subscribePushToken(subscription: (String?) -> Unit): String {
        val subscriptionId = UUID.randomUUID().toString()

        if (SharedPreferencesManager.isInitialized() && !MindboxPreferences.isFirstInitialize) {
            subscription.invoke(MindboxPreferences.pushToken)
        } else {
            tokenCallbacks[subscriptionId] = subscription
        }

        return subscriptionId
    }

    /**
     * Removes push token subscription if it is no longer necessary
     *
     * @param subscriptionId - identifier of the subscription to remove
     */
    @Deprecated(
        message = "Use disposePushTokenSubscription",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("disposePushTokenSubscription"),
    )
    fun disposeFmsTokenSubscription(
        subscriptionId: String,
    ) = disposePushTokenSubscription(subscriptionId)

    /**
     * Removes push token subscription if it is no longer necessary
     *
     * @param subscriptionId - identifier of the subscription to remove
     */
    fun disposePushTokenSubscription(subscriptionId: String) {
        tokenCallbacks.remove(subscriptionId)
    }

    /**
     * Returns date of push token saving
     */
    @Deprecated(
        message = "Use getPushTokenSaveDate instead",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("getPushTokenSaveDate"),
    )
    fun getFmsTokenSaveDate() = getPushTokenSaveDate()

    /**
     * Returns date of push token saving
     */
    fun getPushTokenSaveDate(): String = LoggingExceptionHandler.runCatching(defaultValue = "") {
        MindboxPreferences.tokenSaveDate
    }

    /**
     * Returns SDK version
     */
    fun getSdkVersion(): String = LoggingExceptionHandler.runCatching(defaultValue = "") {
        BuildConfig.VERSION_NAME
    }

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
     * Updates push token for SDK
     * Call it from onNewToken in messaging service
     *
     * @param context used to initialize the main tools
     * @param token - token of push service
     */
    fun updatePushToken(context: Context, token: String) = LoggingExceptionHandler.runCatching {
        if (token.trim().isNotEmpty()) {
            initComponents(context)

            if (!MindboxPreferences.isFirstInitialize) {
                mindboxScope.launch {
                    updateAppInfo(context, token)
                }
            }
        }
    }

    /**
     * Creates and deliveries event of "Push delivered". Recommended call this method from
     * background thread.
     *
     * @param context used to initialize the main tools
     * @param uniqKey - unique identifier of push notification
     */
    fun onPushReceived(context: Context, uniqKey: String) = LoggingExceptionHandler.runCatching {
        initComponents(context)
        MindboxEventManager.pushDelivered(context, uniqKey)

        if (!MindboxPreferences.isFirstInitialize) {
            mindboxScope.launch {
                updateAppInfo(context)
            }
        }
    }

    /**
     * Creates and deliveries event of "Push clicked". Recommended call this method from background
     * thread.
     *
     * @param context used to initialize the main tools
     * @param uniqKey - unique identifier of push notification
     * @param buttonUniqKey - unique identifier of push notification button
     */
    fun onPushClicked(
        context: Context,
        uniqKey: String,
        buttonUniqKey: String?,
    ) = LoggingExceptionHandler.runCatching {
        initComponents(context)
        MindboxEventManager.pushClicked(context, TrackClickData(uniqKey, buttonUniqKey))

        if (!MindboxPreferences.isFirstInitialize) {
            mindboxScope.launch {
                updateAppInfo(context)
            }
        }
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
    fun onPushClicked(
        context: Context,
        intent: Intent,
    ): Boolean = LoggingExceptionHandler.runCatching(defaultValue = false) {
        PushNotificationManager.getUniqKeyFromPushIntent(intent)
            ?.let { uniqKey ->
                val pushButtonUniqKey = PushNotificationManager
                    .getUniqPushButtonKeyFromPushIntent(intent)
                onPushClicked(context, uniqKey, pushButtonUniqKey)
                true
            }
            ?: false
    }

    /**
     * Initializes the SDK for further work.
     *
     * We recommend calling it synchronously in onCreate on an application class
     *
     * If you must call it the other way, invoke [Mindbox.setPushServiceHandler] in [Application.onCreate] or else pushes won't be shown when application is inactive
     *
     * @param context used to initialize the main tools
     * @param configuration contains the data that is needed to connect to the Mindbox
     * @param pushServices list, containing [MindboxPushService]s, i.e.
     * ```
     *     listOf(MindboxFirebase, MindboxHuawei)
     * ```
     */
    fun init(
        context: Context,
        configuration: MindboxConfiguration,
        pushServices: List<MindboxPushService>,
    ) {
        LoggingExceptionHandler.runCatching {
            initComponents(context, pushServices)

            initScope.launch {
                val checkResult = checkConfig(configuration)

                if (checkResult != ConfigUpdate.NOT_UPDATED && !MindboxPreferences.isFirstInitialize) {
                    softReinitialization(context, checkResult, configuration)
                }

                if (checkResult == ConfigUpdate.UPDATED) {
                    val validatedConfiguration = validateConfiguration(configuration)
                    firstInitialization(context, validatedConfiguration)

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
                        MindboxLoggerImpl.e(
                            this@Mindbox,
                            "Incorrect context type for calling init in this place",
                        )
                    }
                    if (isApplicationResumed || context !is Application) {
                        MindboxLoggerImpl.w(
                            this,
                            "We recommend to call Mindbox.init() synchronously from " +
                                    "Application.onCreate. If you can't do so, don't forget to " +
                                    "call Mindbox.initPushServices from Application.onCreate",
                        )
                    }

                    lifecycleManager = LifecycleManager(
                        currentActivityName = activity?.javaClass?.name,
                        currentIntent = activity?.intent,
                        isAppInBackground = !isApplicationResumed,
                        onAppMovedToForeground = {
                            mindboxScope.launch {
                                if (!MindboxPreferences.isFirstInitialize) {
                                    updateAppInfo(context)
                                }
                            }
                        },
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
        }
    }

    /**
     * Method to initialise push services
     *
     * You must call this method in onCreate in your Application class if you call [Mindbox.init] not there
     *
     * @param context used to initialize the main tools
     * @param pushServices list, containing [MindboxPushService]s, i.e.
     * ```
     *     listOf(MindboxFirebase, MindboxHuawei)
     * ```
     */
    fun initPushServices(
        context: Context,
        pushServices: List<MindboxPushService>,
    ) = initComponents(context, pushServices)

    private fun setPushServiceHandler(
        context: Context,
        pushServices: List<MindboxPushService>? = null,
    ) = LoggingExceptionHandler.runCatching {
        if (pushServiceHandler == null && pushServices != null) {
            val savedProvider = MindboxPreferences.notificationProvider
            selectPushServiceHandler(context, pushServices, savedProvider)
                ?.let { pushServiceHandler ->
                    this.pushServiceHandler = pushServiceHandler
                    pushServiceHandler.notificationProvider
                        .takeIf { it != savedProvider }
                        ?.let { newProvider ->
                            MindboxPreferences.notificationProvider = newProvider
                            if (!MindboxPreferences.isFirstInitialize) {
                                mindboxScope.launch {
                                    updateAppInfo(context)
                                }
                            }
                        }
                    mindboxScope.launch {
                        pushServiceHandler.initService(context)
                    }
                }
        }
    }

    private fun createMindboxScope() = CoroutineScope(
        Default + SupervisorJob() + coroutineExceptionHandler,
    )

    private fun selectPushServiceHandler(
        context: Context,
        pushServices: List<MindboxPushService>,
        savedProvider: String,
    ): PushServiceHandler? {
        val serviceHandlers = pushServices
            .map { it.getServiceHandler(MindboxLoggerImpl, LoggingExceptionHandler) }
        return serviceHandlers.firstOrNull { it.notificationProvider == savedProvider }
            ?: initAvailablePushService(context, serviceHandlers, savedProvider)
    }

    private fun initAvailablePushService(
        context: Context,
        serviceHandlers: List<PushServiceHandler>,
        savedProvider: String,
    ) = if (savedProvider.isBlank()) {
        serviceHandlers.firstOrNull { it.isServiceAvailable(context) }
    } else {
        MindboxLoggerImpl.e(
            Mindbox,
            "Mindbox was previously initialized with $savedProvider push service but " +
                    "Mindbox did not find it within pushServices. Check your Mindbox.init() and " +
                    "Mindbox.initPushServices()",
        )
        null
    }

    /**
     * Send track visit event after link or push was clicked for [Activity] with launchMode equals
     * "singleTop" or "singleTask" or if a client used the [Intent.FLAG_ACTIVITY_SINGLE_TOP] or
     * [Intent.FLAG_ACTIVITY_NEW_TASK]
     * flag when calling {@link #startActivity}.
     *
     * @param intent new intent for activity, which was received in [Activity.onNewIntent] method
     */
    fun onNewIntent(intent: Intent?) = LoggingExceptionHandler.runCatching {
        if (Mindbox::lifecycleManager.isInitialized) {
            lifecycleManager.onNewIntent(intent)
        }
    }

    /**
     * Specifies log level for Mindbox
     *
     * @param level - is used for showing Mindbox logs starts from [Level]. Default
     * is [Level.INFO]. [Level.NONE] turns off all logs.
     */
    fun setLogLevel(level: Level) {
        MindboxLoggerImpl.level = level
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
        operationBody: T,
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
        operationBody: T,
    ) = asyncOperation(context, operationSystemName, operationBody)

    /**
     * Creates and deliveries event with specified name and body. Recommended call this method from
     * background thread.
     *
     * @param context current context is used
     * @param operationSystemName the name of asynchronous operation
     * @param operationBodyJson event json body of operation.
     */
    fun executeAsyncOperation(
        context: Context,
        operationSystemName: String,
        operationBodyJson: String,
    ) = asyncOperation(context, operationSystemName, operationBodyJson)

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
        onError: (MindboxError) -> Unit,
    ): Unit = executeSyncOperation(
        context = context,
        operationSystemName = operationSystemName,
        operationBody = operationBody,
        classOfV = OperationResponse::class.java,
        onSuccess = onSuccess,
        onError = onError,
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
        onError: (MindboxError) -> Unit,
    ) {
        if (validateOperationAndInitializeComponents(context, operationSystemName)) {
            mindboxScope.launch {
                MindboxEventManager.syncOperation(
                    context = context,
                    name = operationSystemName,
                    body = operationBody,
                    classOfV = classOfV,
                    onSuccess = onSuccess,
                    onError = onError,
                )
            }
        }
    }

    /**
     * Creates and deliveries event synchronously with specified name and body.
     *
     * @param context current context is used
     * @param operationSystemName the name of synchronous operation
     * @param operationBodyJson event json body of operation.
     * @param onSuccess Callback that will be invoked for success response to a given request.
     * @param onError Callback for response typed [MindboxError] and will be invoked for error response to a given request.
     */
    fun executeSyncOperation(
        context: Context,
        operationSystemName: String,
        operationBodyJson: String,
        onSuccess: (String) -> Unit,
        onError: (MindboxError) -> Unit,
    ) {
        if (validateOperationAndInitializeComponents(context, operationSystemName)) {
            mindboxScope.launch {
                MindboxEventManager.syncOperation(
                    context = context,
                    name = operationSystemName,
                    bodyJson = operationBodyJson,
                    onSuccess = onSuccess,
                    onError = onError,
                )
            }
        }
    }

    /**
     * Handles only Mindbox notification message from [HmsMessageService] or [FirebaseMessageServise].
     *
     * @param context context used for Mindbox initializing and push notification showing
     * @param message the [RemoteMessage] received from Firebase or HMS
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
        message: Any?,
        channelId: String,
        channelName: String,
        @DrawableRes pushSmallIcon: Int,
        defaultActivity: Class<out Activity>,
        channelDescription: String? = null,
        activities: Map<String, Class<out Activity>>? = null,
    ): Boolean = LoggingExceptionHandler.runCatching(defaultValue = false) {
        val convertedMessage = message?.let {
            pushServiceHandler?.convertToRemoteMessage(message)
        } ?: return@runCatching false

        runBlocking(mindboxScope.coroutineContext) {
            PushNotificationManager.handleRemoteMessage(
                context = context,
                remoteMessage = convertedMessage,
                channelId = channelId,
                channelName = channelName,
                pushSmallIcon = pushSmallIcon,
                channelDescription = channelDescription,
                activities = activities,
                defaultActivity = defaultActivity,
            )
        }
    }

    /**
     * Retrieves url from intent generated by notification manager
     *
     * @param intent an intent sent by SDK and received in activity
     * @return url associated with the push intent or null if there is none
     */
    fun getUrlFromPushIntent(
        intent: Intent?,
    ): String? = intent?.let(PushNotificationManager::getUrlFromPushIntent)

    /**
     * Retrieves payload from intent generated by notification manager
     *
     * @param intent an intent sent by SDK and received in activity
     * @return payload delivered in push or null if there is none
     */
    fun getPayloadFromPushIntent(
        intent: Intent?,
    ) = intent?.let(PushNotificationManager::getPayloadFromPushIntent)

    private fun deliverToken(token: String?) {
        Executors.newSingleThreadScheduledExecutor().schedule({
            tokenCallbacks.keys.asIterable().forEach { key ->
                tokenCallbacks[key]?.invoke(token)
                tokenCallbacks.remove(key)
            }
        }, DELIVER_TOKEN_DELAY, TimeUnit.SECONDS)
    }

    internal fun initComponents(context: Context, pushServices: List<MindboxPushService>? = null) {
        SharedPreferencesManager.with(context)
        DbManager.init(context)
        setPushServiceHandler(context, pushServices)
    }

    private fun <T> asyncOperation(
        context: Context,
        operationSystemName: String,
        operationBody: T,
    ) = LoggingExceptionHandler.runCatching {
        asyncOperation(
            context,
            operationSystemName,
            MindboxEventManager.operationBodyJson(operationBody),
        )
    }

    private fun asyncOperation(
        context: Context,
        operationSystemName: String,
        operationBodyJson: String,
    ) {
        if (validateOperationAndInitializeComponents(context, operationSystemName)) {
            MindboxEventManager.asyncOperation(context, operationSystemName, operationBodyJson)
        }
    }

    private fun validateOperationAndInitializeComponents(
        context: Context,
        operationSystemName: String,
    ) = LoggingExceptionHandler.runCatching(defaultValue = false) {
        if (operationSystemName.matches(OPERATION_NAME_REGEX.toRegex())) {
            initComponents(context)
        } else {
            MindboxLoggerImpl.w(
                this,
                "Operation name is incorrect. It should contain only latin letters, number, '-' or '.' and length from 1 to 250.",
            )
        }
        true
    }

    private suspend fun getDeviceId(
        context: Context,
    ): String = if (MindboxPreferences.isFirstInitialize) {
        val adid = mindboxScope.async {
            pushServiceHandler?.getAdsIdentification(context) ?: generateRandomUuid()
        }
        adid.await()
    } else {
        MindboxPreferences.deviceUuid
    }

    private suspend fun firstInitialization(
        context: Context,
        configuration: MindboxConfiguration,
    ) = LoggingExceptionHandler.runCatchingSuspending {
        val pushToken = withContext(mindboxScope.coroutineContext) {
            pushServiceHandler?.registerToken(context, MindboxPreferences.pushToken)
        }

        val isNotificationEnabled = PushNotificationManager.isNotificationsEnabled(context)
        val deviceUuid = getDeviceId(context)
        val instanceId = generateRandomUuid()

        DbManager.saveConfigurations(Configuration(configuration))

        val isTokenAvailable = !pushToken.isNullOrEmpty()
        val notificationProvider = pushServiceHandler?.notificationProvider ?: ""
        val initData = InitData(
            token = pushToken ?: "",
            isTokenAvailable = isTokenAvailable,
            installationId = configuration.previousInstallationId,
            externalDeviceUUID = configuration.previousDeviceUUID,
            isNotificationsEnabled = isNotificationEnabled,
            subscribe = configuration.subscribeCustomerIfCreated,
            instanceId = instanceId,
            notificationProvider = notificationProvider,
        )

        MindboxPreferences.deviceUuid = deviceUuid
        MindboxPreferences.pushToken = pushToken
        MindboxPreferences.isNotificationEnabled = isNotificationEnabled
        MindboxPreferences.instanceId = instanceId
        MindboxPreferences.notificationProvider = notificationProvider

        MindboxEventManager.appInstalled(context, initData, configuration.shouldCreateCustomer)

        deliverDeviceUuid(deviceUuid)
        deliverToken(pushToken)
    }

    private suspend fun updateAppInfo(
        context: Context,
        token: String? = null,
    ) = withContext(infoUpdatedThreadDispatcher) {
        LoggingExceptionHandler.runCatchingSuspending {

            val pushToken = token ?: withContext(mindboxScope.coroutineContext) {
                pushServiceHandler?.registerToken(context, MindboxPreferences.pushToken)
            }

            val isTokenAvailable = !pushToken.isNullOrEmpty()

            val isNotificationEnabled = PushNotificationManager.isNotificationsEnabled(context)

            if (isUpdateInfoRequired(isTokenAvailable, pushToken, isNotificationEnabled)) {
                val initData = UpdateData(
                    token = pushToken ?: MindboxPreferences.pushToken ?: "",
                    isTokenAvailable = isTokenAvailable,
                    isNotificationsEnabled = isNotificationEnabled,
                    instanceId = MindboxPreferences.instanceId,
                    version = MindboxPreferences.infoUpdatedVersion,
                    notificationProvider = pushServiceHandler?.notificationProvider ?: "",
                )

                MindboxEventManager.appInfoUpdate(context, initData)

                MindboxPreferences.isNotificationEnabled = isNotificationEnabled
                MindboxPreferences.pushToken = pushToken
            }
        }
    }

    private fun isUpdateInfoRequired(
        isTokenAvailable: Boolean,
        pushToken: String?,
        isNotificationEnabled: Boolean,
    ) = isTokenAvailable && pushToken != MindboxPreferences.pushToken
            || isNotificationEnabled != MindboxPreferences.isNotificationEnabled

    private fun checkConfig(
        newConfiguration: MindboxConfiguration,
    ): ConfigUpdate = LoggingExceptionHandler.runCatching(ConfigUpdate.UPDATED) {
        if (MindboxPreferences.isFirstInitialize) {
            ConfigUpdate.UPDATED
        } else {
            DbManager.getConfigurations()?.let { currentConfiguration ->
                val isUrlChanged = newConfiguration.domain != currentConfiguration.domain
                val isEndpointChanged =
                    newConfiguration.endpointId != currentConfiguration.endpointId
                val isShouldCreateCustomerChanged =
                    newConfiguration.shouldCreateCustomer != currentConfiguration.shouldCreateCustomer

                when {
                    isUrlChanged || isEndpointChanged -> ConfigUpdate.UPDATED
                    !isShouldCreateCustomerChanged -> ConfigUpdate.NOT_UPDATED
                    currentConfiguration.shouldCreateCustomer &&
                            !newConfiguration.shouldCreateCustomer -> ConfigUpdate.UPDATED_SCC
                    else -> ConfigUpdate.UPDATED
                }
            } ?: ConfigUpdate.UPDATED
        }
    }

    private fun softReinitialization(
        context: Context,
        checkResult: ConfigUpdate,
        configuration: MindboxConfiguration,
    ) {
        mindboxScope.cancel()
        DbManager.removeAllEventsFromQueue()
        BackgroundWorkManager.cancelAllWork(context)

        if (checkResult == ConfigUpdate.UPDATED_SCC) {
            val validatedConfiguration = validateConfiguration(configuration)
            DbManager.saveConfigurations(Configuration(validatedConfiguration))
        }

        MindboxPreferences.resetAppInfoUpdated()
        mindboxScope = createMindboxScope()
    }

    private fun sendTrackVisitEvent(
        context: Context,
        @TrackVisitSource source: String? = null,
        requestUrl: String? = null,
    ) = LoggingExceptionHandler.runCatching {
        DbManager.getConfigurations()?.endpointId?.let { endpointId ->
            val applicationContext = context.applicationContext
            val trackVisitData = TrackVisitData(
                ianaTimeZone = TimeZone.getDefault().id,
                endpointId = endpointId,
                source = source,
                requestUrl = requestUrl,
            )

            MindboxEventManager.appStarted(applicationContext, trackVisitData)
        }
    }

    private fun deliverDeviceUuid(deviceUuid: String) {
        Executors.newSingleThreadScheduledExecutor().schedule({
            deviceUuidCallbacks.keys.asIterable().forEach { key ->
                deviceUuidCallbacks[key]?.invoke(deviceUuid)
                deviceUuidCallbacks.remove(key)
            }
        }, 1, TimeUnit.SECONDS)
    }

    private fun validateConfiguration(configuration: MindboxConfiguration): MindboxConfiguration {
        val validationErrors = SdkValidation.validateConfiguration(
            domain = configuration.domain,
            endpointId = configuration.endpointId,
            previousDeviceUUID = configuration.previousDeviceUUID,
            previousInstallationId = configuration.previousInstallationId,
        )

        return if (validationErrors.isEmpty()) {
            configuration
        } else {
            if (validationErrors.any(SdkValidation.Error::critical)) {
                throw InitializeMindboxException(validationErrors.toString())
            }
            MindboxLoggerImpl.e(this, "Invalid configuration parameters found: $validationErrors")
            val isDeviceIdError = validationErrors.contains(
                SdkValidation.Error.INVALID_DEVICE_ID,
            )
            val isInstallationIdError = validationErrors.contains(
                SdkValidation.Error.INVALID_INSTALLATION_ID,
            )

            val previousDeviceUUID = if (isDeviceIdError) {
                ""
            } else {
                configuration.previousDeviceUUID
            }
            val previousInstallationId = if (isInstallationIdError) {
                ""
            } else {
                configuration.previousInstallationId
            }

            configuration.copy(
                previousDeviceUUID = previousDeviceUUID,
                previousInstallationId = previousInstallationId,
            )
        }
    }

    internal fun generateRandomUuid() = UUID.randomUUID().toString()

}
