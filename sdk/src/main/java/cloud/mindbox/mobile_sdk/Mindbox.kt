@file:Suppress("DEPRECATION")

package cloud.mindbox.mobile_sdk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.WorkerFactory
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManager
import cloud.mindbox.mobile_sdk.logger.*
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
import cloud.mindbox.mobile_sdk.utils.*
import com.jakewharton.threetenabp.AndroidThreeTen
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SuppressWarnings("deprecated")
public object Mindbox : MindboxLog {

    /**
     * Used for determination app open from push
     */
    internal const val IS_OPENED_FROM_PUSH_BUNDLE_KEY = "isOpenedFromPush"

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
    public val mindboxWorkerFactory: WorkerFactory by lazy { MindboxWorkerFactory }

    private const val OPERATION_NAME_REGEX = "^[A-Za-z0-9-\\.]{1,249}\$"
    private const val DELIVER_TOKEN_DELAY = 1L
    private const val INIT_PUSH_SERVICES_TIMEOUT = 5000L

    public val coroutineExceptionHandler: CoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        MindboxLoggerImpl.e(Mindbox, "Mindbox caught unhandled error", throwable)
    }
    private val initScope = createMindboxScope()
    internal var mindboxScope = createMindboxScope()
        private set

    internal var eventScope = createMindboxScope(Dispatchers.IO)
        private set

    private val tokenCallbacks = ConcurrentHashMap<String, (String?) -> Unit>()
    private val deviceUuidCallbacks = ConcurrentHashMap<String, (String) -> Unit>()

    private lateinit var lifecycleManager: LifecycleManager

    private val userVisitManager: UserVisitManager by mindboxInject { userVisitManager }

    internal var pushServiceHandlers: List<PushServiceHandler> = listOf()

    private val inAppMessageManager: InAppMessageManager by mindboxInject { inAppMessageManager }

    private val getDeviceIdMutex = Mutex()
    private val inAppMutex = Mutex()
    private val mutexUpdateAppInfo: Mutex = Mutex()
    private val pushServiceMutex = Mutex()

    private var firstInitCall: Boolean = true

    private val migrationManager: MigrationManager by mindboxInject { migrationManager }

    private val sessionStorageManager: SessionStorageManager by mindboxInject { sessionStorageManager }

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
    public fun setMessageHandling(
        imageFailureHandler: MindboxImageFailureHandler = PushNotificationManager.messageHandler.imageFailureHandler,
        imageLoader: MindboxImageLoader = PushNotificationManager.messageHandler.imageLoader,
    ) {
        MindboxLoggerImpl.d(
            this, "setMessageHandling " +
                "imageFailureHandler: ${imageFailureHandler.javaClass.simpleName}, " +
                "imageLoader: ${imageLoader.javaClass.simpleName}"
        )
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
        message = "Use subscribePushTokens instead",
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("subscribePushTokens"),
    )
    public fun subscribeFmsToken(subscription: (String?) -> Unit): String {
        logW("Called subscribeFmsToken")
        return subscribePushToken(subscription)
    }

    /**
     * Subscribe to gets token from push service used by SDK
     *
     * @param subscription - invocation function with push token
     * @return String identifier of subscription
     * @see disposePushTokenSubscription
     */
    @Deprecated(
        message = "Use subscribePushTokens instead",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("subscribePushTokens"),
    )
    public fun subscribePushToken(subscription: (String?) -> Unit): String {
        logW("Called subscribePushToken")
        val subscriptionId = "Subscription-${UUID.randomUUID()} " +
            "(USE THIS ONLY TO UNSUBSCRIBE FROM 'PushToken' " +
            "IN Mindbox.disposePushTokenSubscription(...))"

        if (SharedPreferencesManager.isInitialized() && !MindboxPreferences.isFirstInitialize) {
            subscription.invoke(
                MindboxPreferences.pushTokens
                    .entries
                    .takeIf { it.isNotEmpty() }
                    ?.maxBy { it.value.updateDate }
                    ?.value
                    ?.token
            )
        } else {
            tokenCallbacks[subscriptionId] = subscription
        }

        return subscriptionId
    }

    /**
     * Subscribe to gets tokens from push services used by SDK
     *
     * @param subscription - invocation function with json object with push tokens
     * example: {"FCM":"token1","HMS":"token2"}
     * @return String identifier of subscription
     * @see disposePushTokenSubscription
     */
    public fun subscribePushTokens(subscription: (String?) -> Unit): String {
        logI("Called subscribePushTokens")
        val subscriptionId = "Subscription-${UUID.randomUUID()} " +
            "(USE THIS ONLY TO UNSUBSCRIBE FROM 'PushToken' " +
            "IN Mindbox.disposePushTokenSubscription(...))"

        val getPushTokens: (String?) -> Unit = {
            subscription(
                MindboxPreferences
                    .pushTokens
                    .mapValues { it.value.token }
                    .toPreferences()
            )
        }
        if (SharedPreferencesManager.isInitialized() && !MindboxPreferences.isFirstInitialize) {
            getPushTokens.invoke(subscriptionId)
        } else {
            tokenCallbacks[subscriptionId] = getPushTokens
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
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("disposePushTokenSubscription"),
    )
    public fun disposeFmsTokenSubscription(
        subscriptionId: String,
    ) {
        MindboxLoggerImpl.d(this, "disposeFmsTokenSubscription")
        disposePushTokenSubscription(subscriptionId)
    }

    /**
     * Removes push token subscription if it is no longer necessary
     *
     * @param subscriptionId - identifier of the subscription to remove
     */
    public fun disposePushTokenSubscription(subscriptionId: String) {
        MindboxLoggerImpl.d(this, "disposePushTokenSubscription")
        tokenCallbacks.remove(subscriptionId)
    }

    /**
     * Returns date of push token saving
     */
    @Deprecated(
        message = "Use getPushTokensSaveDate instead",
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("getPushTokenSaveDate"),
    )
    public fun getFmsTokenSaveDate(): String {
        logW("Used deprecated getFmsTokenSaveDate")

        @Suppress("DEPRECATION_ERROR")
        return getPushTokenSaveDate()
    }

    /**
     * Returns date of push token saving
     */
    @Deprecated(
        message = "Use getPushTokensSaveDate instead",
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("getPushTokenSaveDate"),
    )
    public fun getPushTokenSaveDate(): String = loggingRunCatching(defaultValue = "") {
        logW("Called getPushTokenSaveDate")
        MindboxPreferences.pushTokens
            .entries
            .maxOf { it.value.updateDate }
            .let { Date(it).toString() }
    }

    /**
     * Returns map of push tokens with providers and save dates
     */
    public fun getPushTokensSaveDate(): Map<String, Long> = loggingRunCatching(defaultValue = emptyMap()) {
        logI("Called getPushTokensSaveDate")
        MindboxPreferences.pushTokens.map { (provider, token) ->
            provider to token.updateDate
        }.toMap()
    }

    /**
     * Returns SDK version
     */
    public fun getSdkVersion(): String = LoggingExceptionHandler.runCatching(defaultValue = "") {
        MindboxLoggerImpl.d(this, "getSdkVersion")
        BuildConfig.VERSION_NAME
    }

    /**
     * Subscribe to gets deviceUUID used by SDK
     *
     * @param subscription - invocation function with deviceUUID
     * @return String identifier of subscription
     * @see disposeDeviceUuidSubscription
     */
    public fun subscribeDeviceUuid(subscription: (String) -> Unit): String {
        MindboxLoggerImpl.d(this, "subscribeDeviceUuid")
        val subscriptionId = "Subscription-${UUID.randomUUID()} " +
            "(USE THIS ONLY TO UNSUBSCRIBE FROM DeviceUuid " +
            "IN Mindbox.disposeDeviceUuidSubscription(...))"

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
    public fun disposeDeviceUuidSubscription(subscriptionId: String) {
        MindboxLoggerImpl.d(this, "disposeDeviceUuidSubscription")
        deviceUuidCallbacks.remove(subscriptionId)
    }

    /**
     * Updates push token for SDK
     * Call it from onNewToken in messaging service
     *
     * @param context used to initialize the main tools
     * @param token - token of push service
     */
    @Deprecated(
        message = "Use updatePushToken(context: Context, token: String, services: MindboxPushService)",
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("this.updatePushToken(context, token, pushService)")
    )
    public fun updatePushToken(context: Context, token: String): Unit = LoggingExceptionHandler.runCatching {
        initComponents(context)
        mindboxLogW("Used deprecated updatePushToken. token: $token")
        if (token.trim().isNotEmpty()) {
            if (!MindboxPreferences.isFirstInitialize) {
                mindboxScope.launch {
                    pushServiceHandlers.firstOrNull()?.let { handler ->
                        if (pushServiceHandlers.size == 1) {
                            updateAppInfo(context, PushToken(handler.notificationProvider, token))
                        } else {
                            updateAppInfo(context)
                        }
                    }
                }
            } else {
                mindboxLogI("updatePushToken. MindboxPreferences.isFirstInitialize == true. Skipping update.")
            }
        }
    }

    /**
     * Updates push token for SDK
     * Call it from onNewToken in messaging service
     *
     * @param context used to initialize the main tools
     * @param token - token of push service
     * @param pushService - the instance of [MindboxPushService], which handles push notifications.
     */
    public fun updatePushToken(context: Context, token: String, pushService: MindboxPushService): Unit =
        loggingRunCatching {
            initComponents(context)
            mindboxLogI("updatePushToken token: $token with provider $pushService")

            if (token.trim().isEmpty()) {
                mindboxLogW("Token is empty! Skipping update token.")
                return@loggingRunCatching
            }

            if (MindboxPreferences.isFirstInitialize) {
                mindboxLogW("Mindbox init was never called. Skipping update token.")
                return@loggingRunCatching
            }

            val notificationProvider = pushServiceHandlers.firstOrNull { handler ->
                pushService.tag == handler.notificationProvider
            }

            if (notificationProvider == null) {
                mindboxLogW("Unknown token provider ${pushService.tag}. Skipping token update.")
                return@loggingRunCatching
            }

            mindboxScope.launch {
                updateAppInfo(context, PushToken(pushService.tag, token))
            }
        }

    /**
     * This method is used to inform when the notification permission status changed to "allowed"
     * @param context current context is used
     **/
    public fun updateNotificationPermissionStatus(context: Context): Unit = LoggingExceptionHandler.runCatching {
        mindboxLogI("updateNotificationPermissionStatus was called")
        mindboxScope.launch {
            InitializeLock.await(InitializeLock.State.SAVE_MINDBOX_CONFIG)
            updateAppInfo(context)
        }
    }

    /**
     * Creates and deliveries event of "Push delivered". Recommended call this method from
     * background thread.
     *
     * Use this method only if you have custom push handling you don't use [Mindbox.handleRemoteMessage].
     * You must not call it otherwise.
     *
     * @param context used to initialize the main tools
     * @param uniqKey - unique identifier of push notification
     */
    public fun onPushReceived(context: Context, uniqKey: String): Unit = LoggingExceptionHandler.runCatching {
        initComponents(context)
        MindboxLoggerImpl.d(this, "onPushReceived. uniqKey: $uniqKey")

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
    public fun onPushClicked(
        context: Context,
        uniqKey: String,
        buttonUniqKey: String?,
    ): Unit = LoggingExceptionHandler.runCatching {
        initComponents(context)
        MindboxLoggerImpl.d(this, "onPushClicked. uniqKey: $uniqKey, buttonUniqKey: $buttonUniqKey")
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
     * @param intent - intent received in app component
     *
     * @return true if Mindbox SDK recognises push intent as Mindbox SDK push intent
     *         false if Mindbox SDK cannot find critical information in intent
     */
    public fun onPushClicked(
        context: Context,
        intent: Intent,
    ): Boolean = LoggingExceptionHandler.runCatching(defaultValue = false) {
        MindboxLoggerImpl.d(this, "onPushClicked with intent")
        intent.getMindboxUniqKeyFromPushIntent()?.let { uniqKey ->
            val pushButtonUniqKey = intent.getMindboxUniqPushButtonKeyFromPushIntent()
            onPushClicked(context, uniqKey, pushButtonUniqKey)
            true
        } ?: false
    }

    /**
     * Initializes the SDK for further work.
     *
     * This method must be called synchronously in onCreate on an application class
     *
     * If you must call it the other way, invoke [Mindbox.setPushServiceHandler] in [Application.onCreate] or else pushes won't be shown when application is inactive
     *
     * @param application used to initialize the main tools
     * @param configuration contains the data that is needed to connect to the Mindbox
     * @param pushServices list, containing [MindboxPushService]s, i.e.
     * ```
     *     listOf(MindboxFirebase, MindboxHuawei, MindboxRuStore)
     * ```
     */
    @MainThread
    public fun init(
        application: Application,
        configuration: MindboxConfiguration,
        pushServices: List<MindboxPushService>,
    ) {
        logI("Initialization with application started")
        initialize(application, configuration, pushServices)
    }

    /**
     * Initializes the SDK for further work.
     *
     * This method should be called in
     * [Activity.onCreate] and should be used if you're unable to call [Mindbox.init] in [Application.onCreate] on an application class
     *
     * If you use this method, invoke [Mindbox.setPushServiceHandler] in [Application.onCreate] or else pushes won't be shown when application is inactive
     *
     * @param activity used to initialize the main tools
     * @param configuration contains the data that is needed to connect to the Mindbox
     * @param pushServices list, containing [MindboxPushService]s, i.e.
     * ```
     *     listOf(MindboxFirebase, MindboxHuawei, MindboxRuStore)
     * ```
     */
    @MainThread
    public fun init(
        activity: Activity,
        configuration: MindboxConfiguration,
        pushServices: List<MindboxPushService>,
    ) {
        logI("Initialization with activity started")
        initialize(activity, configuration, pushServices)
    }

    private fun initialize(
        context: Context,
        configuration: MindboxConfiguration,
        pushServices: List<MindboxPushService>,
    ) {
        LoggingExceptionHandler.runCatching {
            verifyThreadExecution(methodName = "init")
            val currentProcessName = context.getCurrentProcessName()
            if (!context.isMainProcess(currentProcessName)) {
                logW("Skip Mindbox init not in main process! Current process $currentProcessName")
                return@runCatching
            }
            Stopwatch.start(Stopwatch.INIT_SDK)

            initComponents(context.applicationContext)
            logI("init in $currentProcessName. firstInitCall: $firstInitCall, " +
                "configuration: $configuration, pushServices: " +
                pushServices.joinToString(", ") { it.javaClass.simpleName } + ", SdkVersion:${getSdkVersion()}")

            if (!firstInitCall) {
                InitializeLock.reset(InitializeLock.State.SAVE_MINDBOX_CONFIG)
            } else {
                userVisitManager.saveUserVisit()
            }

            initScope.launch {
                InitializeLock.await(InitializeLock.State.MIGRATION)
                val checkResult = checkConfig(configuration)
                val validatedConfiguration = validateConfiguration(configuration)
                DbManager.saveConfigurations(Configuration(configuration))
                logI("init. checkResult: $checkResult")
                if (checkResult != ConfigUpdate.NOT_UPDATED && !MindboxPreferences.isFirstInitialize) {
                    logI("init. softReinitialization")
                    softReinitialization(context.applicationContext)
                }

                if (checkResult == ConfigUpdate.UPDATED) {
                    setPushServiceHandler(context, pushServices)
                    firstInitialization(context.applicationContext, validatedConfiguration)

                    val isTrackVisitNotSent = Mindbox::lifecycleManager.isInitialized &&
                        !lifecycleManager.isTrackVisitSent()
                    if (isTrackVisitNotSent) {
                        MindboxLoggerImpl.d(this, "Track visit event with source $DIRECT")
                        sendTrackVisitEvent(context.applicationContext, DIRECT)
                    }
                } else {
                    mindboxScope.launch {
                        setPushServiceHandler(context, pushServices)
                    }
                    MindboxEventManager.sendEventsIfExist(context.applicationContext)
                }
                MindboxPreferences.uuidDebugEnabled = configuration.uuidDebugEnabled
            }.initState(InitializeLock.State.SAVE_MINDBOX_CONFIG)
                .invokeOnCompletion { throwable ->
                    if (throwable == null) {
                        if (firstInitCall) {
                            val activity = context as? Activity
                            if (activity != null && lifecycleManager.isCurrentActivityResumed) {
                                inAppMessageManager.registerCurrentActivity(activity)
                                mindboxScope.launch {
                                    inAppMutex.withLock {
                                        firstInitCall = false
                                        inAppMessageManager.listenEventAndInApp()
                                        inAppMessageManager.initLogs()
                                        MindboxEventManager.eventFlow.emit(MindboxEventManager.appStarted())
                                        inAppMessageManager.requestConfig().join()
                                    }
                                }
                            }
                        }
                    }
                }
            // Handle back app in foreground
            (context.applicationContext as? Application)?.apply {
                val applicationLifecycle = ProcessLifecycleOwner.get().lifecycle

                if (!Mindbox::lifecycleManager.isInitialized) {
                    val activity = context as? Activity
                    val isApplicationResumed = applicationLifecycle.currentState == RESUMED
                    if (isApplicationResumed && activity == null) {
                        logE("Incorrect context type for calling init in this place")
                    }
                    if (isApplicationResumed || context !is Application) {
                        logW(
                            "We recommend to call Mindbox.init() synchronously from " +
                                "Application.onCreate. If you can't do so, don't forget to " +
                                "call Mindbox.initPushServices from Application.onCreate",
                        )
                    }

                    logI("init. init lifecycleManager")
                    lifecycleManager = LifecycleManager(
                        currentActivityName = activity?.javaClass?.name,
                        currentIntent = activity?.intent,
                        isAppInBackground = !isApplicationResumed,
                        onActivityStarted = { startedActivity ->
                            UuidCopyManager.onAppMovedToForeground(startedActivity)
                            mindboxScope.launch {
                                if (!MindboxPreferences.isFirstInitialize) {
                                    updateAppInfo(startedActivity.applicationContext)
                                }
                            }
                        },
                        onActivityPaused = { pausedActivity ->
                            inAppMessageManager.onPauseCurrentActivity(pausedActivity)
                        },
                        onActivityResumed = { resumedActivity ->
                            inAppMessageManager.onResumeCurrentActivity(
                                resumedActivity
                            )
                            if (firstInitCall) {
                                mindboxScope.launch {
                                    InitializeLock.await(InitializeLock.State.SAVE_MINDBOX_CONFIG)
                                    inAppMutex.withLock {
                                        if (!firstInitCall) return@launch
                                        firstInitCall = false
                                        inAppMessageManager.listenEventAndInApp()
                                        inAppMessageManager.initLogs()
                                        MindboxEventManager.eventFlow.emit(MindboxEventManager.appStarted())
                                        inAppMessageManager.requestConfig().join()
                                    }
                                }
                            }
                        },
                        onActivityStopped = { resumedActivity ->
                            inAppMessageManager.onStopCurrentActivity(resumedActivity)
                        },
                        onTrackVisitReady = { source, requestUrl ->
                            sessionStorageManager.hasSessionExpired()
                            eventScope.launch {
                                sendTrackVisitEvent(
                                    MindboxDI.appModule.appContext,
                                    source,
                                    requestUrl
                                )
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
     *     listOf(MindboxFirebase, MindboxHuawei, MindboxRuStore)
     * ```
     * @Deprecated Use either [Mindbox.init] with application parameter or [Mindbox.init] with activity parameter
     */
    @Deprecated(
        "Use either Mindbox.init with application parameter or Mindbox.init with activity parameter"
    )
    public fun init(
        context: Context,
        configuration: MindboxConfiguration,
        pushServices: List<MindboxPushService>,
    ) {
        logW("Use either Mindbox.init with application parameter or Mindbox.init with activity parameter")
        initialize(context = context, configuration = configuration, pushServices = pushServices)
    }

    /**
     * Method to register callback for InApp Message
     *
     *  Call this method after you call [Mindbox.init]
     *
     *  @param inAppCallback used to provide required callback implementation
     **/

    public fun registerInAppCallback(inAppCallback: InAppCallback) {
        MindboxLoggerImpl.d(this, "registerInAppCallback")
        inAppMessageManager.registerInAppCallback(inAppCallback)
    }

    /**
     * Method to initialise push services
     *
     * You must call this method in onCreate in your Application class if you call [Mindbox.init] not there
     *
     * @param context used to initialize the main tools
     * @param pushServices list, containing [MindboxPushService]s, i.e.
     * ```
     *     listOf(MindboxFirebase, MindboxHuawei, MindboxRuStore)
     * ```
     */
    @MainThread
    public fun initPushServices(
        context: Context,
        pushServices: List<MindboxPushService>,
    ) {
        verifyThreadExecution(methodName = "initPushServices")
        initComponents(context)
        mindboxScope.launch {
            InitializeLock.await(InitializeLock.State.MIGRATION)
            setPushServiceHandler(context, pushServices)
        }
    }

    private suspend fun setPushServiceHandler(
        context: Context,
        pushServices: List<MindboxPushService>,
    ): Unit = loggingRunCatchingSuspending {
        if (pushServices.isEmpty()) {
            mindboxLogW("initPushServices: Push services list is empty")
            return@loggingRunCatchingSuspending
        }

        if (pushServiceHandlers.isNotEmpty()) {
            mindboxLogI("initPushServices: Push services already initialized")
            return@loggingRunCatchingSuspending
        }

        pushServiceMutex.withLock {
            if (pushServiceHandlers.isNotEmpty()) {
                mindboxLogI("initPushServices: Push services already initialized")
                return@withLock
            }

            mindboxLogI("initPushServices: " + pushServices.joinToString { it.tag })
            Stopwatch.start(Stopwatch.INIT_PUSH_SERVICES)

            pushServiceHandlers = selectPushServiceHandler(context, pushServices)

            pushServiceHandlers.map { handler ->
                mindboxScope.async {
                    runCatching {
                        handler.initService(context)
                    }.getOrNull {
                        mindboxLogE("initPushServices: ${handler.notificationProvider} failed to initialize", it)
                    }
                }
            }.awaitAllWithTimeout(INIT_PUSH_SERVICES_TIMEOUT)

            mindboxLogI("initPushServices completed in " + Stopwatch.stop(Stopwatch.INIT_PUSH_SERVICES))
            mindboxScope.launch {
                if (!MindboxPreferences.isFirstInitialize) {
                    updateAppInfo(context)
                }
            }
        }
    }

    private fun createMindboxScope(dispatcher: CoroutineDispatcher = Default) =
        CoroutineScope(
            dispatcher + SupervisorJob() + coroutineExceptionHandler
        )

    private fun selectPushServiceHandler(
        context: Context,
        pushServices: List<MindboxPushService>,
    ): List<PushServiceHandler> =
        pushServices
            .map { it.getServiceHandler(MindboxLoggerImpl, LoggingExceptionHandler) }
            .filter { it.isServiceAvailable(context) }

    /**
     * Send track visit event after link or push was clicked for [Activity] with launchMode equals
     * "singleTop" or "singleTask" or if a client used the [Intent.FLAG_ACTIVITY_SINGLE_TOP] or
     * [Intent.FLAG_ACTIVITY_NEW_TASK]
     * flag when calling {@link #startActivity}.
     *
     * @param intent new intent for activity, which was received in [Activity.onNewIntent] method
     */
    public fun onNewIntent(intent: Intent?): Unit = LoggingExceptionHandler.runCatching {
        MindboxLoggerImpl.d(this, "onNewIntent. intent: $intent")
        if (Mindbox::lifecycleManager.isInitialized) {
            lifecycleManager.onNewIntent(intent)
        } else {
            MindboxLoggerImpl.d(this, "onNewIntent. LifecycleManager is not initialized. Skipping.")
        }
    }

    /**
     * Specifies log level for Mindbox
     *
     * @param level - is used for showing Mindbox logs starts from [Level]. Default
     * is [Level.WARN]. [Level.NONE] turns off all logs.
     */
    public fun setLogLevel(level: Level) {
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
    public fun <T : OperationBody> executeAsyncOperation(
        context: Context,
        operationSystemName: String,
        operationBody: T,
    ) {
        initComponents(context)
        MindboxLoggerImpl.d(
            this,
            "executeAsyncOperation (deprecated). operationSystemName: $operationSystemName"
        )
        asyncOperation(context, operationSystemName, operationBody)
    }

    /**
     * Creates and deliveries event with specified name and body. Recommended call this method from
     * background thread.
     *
     * @param context current context is used
     * @param operationSystemName the name of asynchronous operation
     * @param operationBody [T] which extends [OperationBodyRequestBase] and will be send as event json body of operation.
     */
    public fun <T : OperationBodyRequestBase> executeAsyncOperation(
        context: Context,
        operationSystemName: String,
        operationBody: T,
    ) {
        initComponents(context)
        MindboxLoggerImpl.d(
            this,
            "executeAsyncOperation. operationSystemName: $operationSystemName"
        )
        asyncOperation(context, operationSystemName, operationBody)
    }

    /**
     * Creates and deliveries event with specified name and body. Recommended call this method from
     * background thread.
     *
     * @param context current context is used
     * @param operationSystemName the name of asynchronous operation
     * @param operationBodyJson event json body of operation.
     */
    public fun executeAsyncOperation(
        context: Context,
        operationSystemName: String,
        operationBodyJson: String,
    ) {
        initComponents(context)
        MindboxLoggerImpl.d(
            this, "executeAsyncOperation (with operationBodyJson). " +
                "operationSystemName: $operationSystemName"
        )
        asyncOperation(context, operationSystemName, operationBodyJson)
    }

    /**
     * Creates and deliveries event synchronously with specified name and body.
     *
     * @param context current context is used
     * @param operationSystemName the name of synchronous operation
     * @param operationBody [T] which extends [OperationBodyRequestBase] and will be send as event json body of operation.
     * @param onSuccess Callback for response typed [OperationResponse] that will be invoked for success response to a given request.
     * @param onError Callback for response typed [MindboxError] and will be invoked for error response to a given request.
     */
    public fun <T : OperationBodyRequestBase> executeSyncOperation(
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
    public fun <T : OperationBodyRequestBase, V : OperationResponseBase> executeSyncOperation(
        context: Context,
        operationSystemName: String,
        operationBody: T,
        classOfV: Class<V>,
        onSuccess: (V) -> Unit,
        onError: (MindboxError) -> Unit,
    ) {
        initComponents(context)
        MindboxLoggerImpl.d(
            this, "executeSyncOperation. " +
                "operationSystemName: $operationSystemName, classOfV: ${classOfV.simpleName}"
        )
        if (validateOperation(operationSystemName)) {
            mindboxScope.launch {
                InitializeLock.await(InitializeLock.State.APP_STARTED)

                MindboxEventManager.syncOperation(
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
    public fun executeSyncOperation(
        context: Context,
        operationSystemName: String,
        operationBodyJson: String,
        onSuccess: (String) -> Unit,
        onError: (MindboxError) -> Unit,
    ) {
        initComponents(context)
        MindboxLoggerImpl.d(
            this, "executeSyncOperation (with operationBodyJson). " +
                "operationSystemName: $operationSystemName, operationBodyJson: $operationBodyJson"
        )
        if (validateOperation(operationSystemName)) {
            mindboxScope.launch {
                InitializeLock.await(InitializeLock.State.APP_STARTED)
                MindboxEventManager.syncOperation(
                    name = operationSystemName,
                    bodyJson = operationBodyJson,
                    onSuccess = onSuccess,
                    onError = onError,
                )
            }
        }
    }

    /**
     * Handles only Mindbox notification message from [HmsMessageService], [FirebaseMessageService], [RuStoreMessagingService].
     *
     * @param context context used for Mindbox initializing and push notification showing
     * @param message the [MindboxRemoteMessage] received from Firebase, HMS, RuStore
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
    @WorkerThread
    public fun handleRemoteMessage(
        context: Context,
        message: Any?,
        channelId: String,
        channelName: String,
        @DrawableRes pushSmallIcon: Int,
        defaultActivity: Class<out Activity>,
        channelDescription: String? = null,
        activities: Map<String, Class<out Activity>>? = null,
    ): Boolean = loggingRunCatching(defaultValue = false) {
        verifyThreadExecution(methodName = "handleRemoteMessage", shouldBeMainThread = false)
        mindboxLogI(
            "handleRemoteMessage. channelId: $channelId, " +
                "channelName: $channelName, channelDescription: $channelDescription, " +
                "defaultActivity: ${defaultActivity.simpleName}, " +
                "activities: ${
                    activities?.map {
                        "${it.key}: ${it.value.simpleName}"
                    }?.joinToString(", ")
                }"
        )
        if (message == null) {
            logI("Cannot handle null message")
            return@loggingRunCatching false
        }
        if (pushServiceHandlers.isEmpty()) {
            logW("No push service handlers found.")
        }
        val convertedMessage = when (message) {
            is MindboxRemoteMessage -> message
            else -> pushServiceHandlers.firstNotNullOfOrNull { handler ->
                handler.convertToRemoteMessage(message)
            }
        }
        if (convertedMessage == null) {
            logW("Cannot convert message: $message")
            return@loggingRunCatching false
        } else {
            logI("Try to handle converted message: $convertedMessage")
        }

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
    public fun getUrlFromPushIntent(
        intent: Intent?,
    ): String? {
        MindboxLoggerImpl.d(this, "getUrlFromPushIntent. intent: $intent")
        return intent?.let(PushNotificationManager::getUrlFromPushIntent)
    }

    /**
     * Retrieves payload from intent generated by notification manager
     *
     * @param intent an intent sent by SDK and received in activity
     * @return payload delivered in push or null if there is none
     */
    public fun getPayloadFromPushIntent(
        intent: Intent?,
    ): String? {
        MindboxLoggerImpl.d(this, "getPayloadFromPushIntent. intent: $intent")
        return intent?.let(PushNotificationManager::getPayloadFromPushIntent)
    }

    /**
     * Writes a log message to the Mindbox logging system with the specified log level.
     * This method should be used only after Mindbox has been initialized by calling `Mindbox.init`.
     * Otherwise, the logs will not be recorded.
     * This method takes a log message and a log level, and routes the message to the appropriate
     * @param message The message to be logged.
     * @param logLevel The severity level of the log message. See [Level].
     */
    public fun writeLog(message: String, logLevel: Level) {
        when (logLevel) {
            Level.VERBOSE -> mindboxLogD(message = message)
            Level.DEBUG -> mindboxLogD(message = message)
            Level.INFO -> mindboxLogI(message = message)
            Level.WARN -> mindboxLogW(message = message)
            Level.ERROR -> mindboxLogE(message = message)
            Level.NONE -> mindboxLogD(message = message)
        }
    }

    private fun deliverToken(tokens: PushTokenMap) {
        Executors.newSingleThreadScheduledExecutor().schedule({
            tokenCallbacks.keys.asIterable().forEach { key ->
                tokenCallbacks[key]?.invoke(tokens.entries.firstOrNull()?.value)
                tokenCallbacks.remove(key)
            }
        }, DELIVER_TOKEN_DELAY, TimeUnit.SECONDS)
    }

    internal fun initComponents(context: Context) {
        MindboxDI.init(context.applicationContext)
        AndroidThreeTen.init(context)
        SharedPreferencesManager.with(context)
        DbManager.init(context)

        mindboxScope.launch {
            migrationManager.migrateAll()
        }
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
        MindboxLoggerImpl.d(this, "asyncOperation. operationBodyJson: $operationBodyJson")
        if (validateOperation(operationSystemName)) {
            initScope.launch {
                InitializeLock.await(InitializeLock.State.APP_STARTED)
                MindboxEventManager.asyncOperation(context, operationSystemName, operationBodyJson)
            }
        }
    }

    private fun validateOperation(
        operationSystemName: String,
    ) = LoggingExceptionHandler.runCatching(defaultValue = false) {
        if (operationSystemName.matches(OPERATION_NAME_REGEX.toRegex())) {
            return@runCatching true
        } else {
            MindboxLoggerImpl.w(
                this,
                "Operation name is incorrect. It should contain only latin letters, number, '-' or '.' and length from 1 to 250.",
            )
            return@runCatching false
        }
    }

    private suspend fun getDeviceId(
        context: Context,
    ): String {
        getDeviceIdMutex.withLock {
            return MindboxPreferences.deviceUuid.ifEmpty {
                val adidResult = mindboxScope.async {
                    pushServiceHandlers.firstNotNullOfOrNull { handler ->
                        handler.getAdsIdentification(context)
                    }
                }.await() ?: generateRandomUuid()

                MindboxPreferences.deviceUuid = adidResult
                adidResult
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun firstInitialization(
        context: Context,
        configuration: MindboxConfiguration,
    ) = loggingRunCatchingSuspending {
        val pushTokens: PushTokenMap = getPushTokens(context, emptyMap())

        val isNotificationEnabled = PushNotificationManager.isNotificationsEnabled(context)
        val deviceUuid = getDeviceId(context)
        val instanceId = generateRandomUuid()

        logI(
            "First SDK initialization with deviceUuid: $deviceUuid, " +
                "pushTokens: $pushTokens, " +
                "isNotificationEnabled: $isNotificationEnabled"
        )

        val timezone = TimeZone.getDefault().id.takeIf { configuration.shouldCreateCustomer }
        val initData = InitData(
            installationId = configuration.previousInstallationId,
            externalDeviceUUID = configuration.previousDeviceUUID,
            isNotificationsEnabled = isNotificationEnabled,
            subscribe = configuration.subscribeCustomerIfCreated,
            instanceId = instanceId,
            ianaTimeZone = timezone,
            tokens = pushTokens.toTokenData(),
        )

        MindboxPreferences.pushTokens = pushTokens.mapValues {
            PrefPushToken(it.value, Date().time)
        }
        MindboxPreferences.isNotificationEnabled = isNotificationEnabled
        MindboxPreferences.instanceId = instanceId

        MindboxEventManager.appInstalled(context, initData, configuration.shouldCreateCustomer)

        deliverDeviceUuid(deviceUuid)
        deliverToken(pushTokens)
    }

    internal suspend fun updateAppInfo(
        context: Context,
        pushToken: PushToken? = null,
    ): Unit = loggingRunCatchingSuspending {
        mutexUpdateAppInfo.withLock {
            val prefsPushTokens = MindboxPreferences.pushTokens
            val savedPushTokens = prefsPushTokens.mapValues { it.value.token }
            val savedIsNotificationEnabled = MindboxPreferences.isNotificationEnabled

            val pushTokens: PushTokenMap =
                if (pushToken != null && pushToken.token == savedPushTokens[pushToken.provider]) {
                    savedPushTokens
                } else {
                    savedPushTokens + getPushTokens(context, savedPushTokens)
                }

            val isNotificationEnabled = PushNotificationManager.isNotificationsEnabled(context)

            if (pushTokens == savedPushTokens && isNotificationEnabled == savedIsNotificationEnabled) {
                return@loggingRunCatchingSuspending
            }

            mindboxLogI(
                "updateAppInfo. pushToken: $pushTokens, isNotificationEnabled: $isNotificationEnabled, " +
                    "old isNotificationEnabled: $savedPushTokens"
            )
            val initData = UpdateData(
                isNotificationsEnabled = isNotificationEnabled,
                instanceId = MindboxPreferences.instanceId,
                version = MindboxPreferences.infoUpdatedVersion,
                tokens = pushTokens.toTokenData(),
            )

            MindboxEventManager.appInfoUpdate(context, initData)

            if (isNotificationEnabled != savedIsNotificationEnabled) {
                MindboxPreferences.isNotificationEnabled = isNotificationEnabled
            }
            if (pushTokens != savedPushTokens) {
                MindboxPreferences.pushTokens = pushTokens.mapValues { (provider, token) ->
                    val prefsToken = prefsPushTokens[provider]
                    PrefPushToken(
                        token,
                        if (token != prefsToken?.token) Date().time else prefsToken.updateDate
                    )
                }
            }
        }
    }

    private fun checkConfig(
        newConfiguration: MindboxConfiguration,
    ): ConfigUpdate = LoggingExceptionHandler.runCatching(ConfigUpdate.UPDATED) {
        MindboxLoggerImpl.d(
            this, "checkConfig. " +
                "isFirstInitialize: ${MindboxPreferences.isFirstInitialize}"
        )
        if (MindboxPreferences.isFirstInitialize) {
            ConfigUpdate.UPDATED
        } else {
            DbManager.getConfigurations()?.let { currentConfiguration ->
                val isUrlChanged = newConfiguration.domain != currentConfiguration.domain
                val isEndpointChanged =
                    newConfiguration.endpointId != currentConfiguration.endpointId
                val isShouldCreateCustomerChanged =
                    newConfiguration.shouldCreateCustomer != currentConfiguration.shouldCreateCustomer

                MindboxLoggerImpl.d(
                    this, "checkConfig. isUrlChanged: $isUrlChanged, " +
                        "isEndpointChanged: $isEndpointChanged, " +
                        "isShouldCreateCustomerChanged: $isShouldCreateCustomerChanged"
                )
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
    ) {
        mindboxScope.cancel()
        DbManager.removeAllEventsFromQueue()
        BackgroundWorkManager.cancelAllWork(context)
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
                sdkVersionNumeric = Constants.SDK_VERSION_NUMERIC
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

    internal fun generateRandomUuid(): String = UUID.randomUUID().toString()
}
