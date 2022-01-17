package cloud.mindbox.mobile_sdk_core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.annotation.DrawableRes
import cloud.mindbox.mobile_sdk_core.logger.Level
import cloud.mindbox.mobile_sdk_core.logger.MindboxLogger
import cloud.mindbox.mobile_sdk_core.managers.*
import cloud.mindbox.mobile_sdk_core.models.*
import cloud.mindbox.mobile_sdk_core.models.operation.request.OperationBodyRequestBase
import cloud.mindbox.mobile_sdk_core.models.operation.response.OperationResponse
import cloud.mindbox.mobile_sdk_core.models.operation.response.OperationResponseBase
import cloud.mindbox.mobile_sdk_core.pushes.PushNotificationManager
import cloud.mindbox.mobile_sdk_core.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk_core.pushes.firebase.FirebaseRemoteMessageTransformer
import cloud.mindbox.mobile_sdk_core.pushes.firebase.FirebaseServiceHandler
import cloud.mindbox.mobile_sdk_core.repository.MindboxPreferences
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object MindboxInternalCore {

    const val IS_OPENED_FROM_PUSH_BUNDLE_KEY = "isOpenedFromPush"

    private const val OPERATION_NAME_REGEX = "^[A-Za-z0-9-\\.]{1,249}\$"

    private val mindboxJob = Job()
    internal val mindboxScope = CoroutineScope(Default + mindboxJob)
    private val deviceUuidCallbacks = ConcurrentHashMap<String, (String) -> Unit>()
    private lateinit var lifecycleManager: LifecycleManager

    internal val pushServiceHandler: PushServiceHandler = FirebaseServiceHandler

    fun subscribeFmsToken(subscription: (String?) -> Unit): String = pushServiceHandler.subscribeToken(subscription)

    fun disposeFmsTokenSubscription(subscriptionId: String) = pushServiceHandler.disposeTokenSubscription(subscriptionId)

    fun getFmsTokenSaveDate(): String = pushServiceHandler.getTokenSaveDate()

    fun getSdkVersion(): String = runCatching { return BuildConfig.VERSION_NAME }
        .returnOnException { "" }

    fun subscribeDeviceUuid(subscription: (String) -> Unit): String {
        val subscriptionId = UUID.randomUUID().toString()

        if (SharedPreferencesManager.isInitialized() && !MindboxPreferences.isFirstInitialize) {
            subscription.invoke(MindboxPreferences.deviceUuid)
        } else {
            deviceUuidCallbacks[subscriptionId] = subscription
        }

        return subscriptionId
    }

    fun disposeDeviceUuidSubscription(subscriptionId: String) {
        deviceUuidCallbacks.remove(subscriptionId)
    }

    fun updateFmsToken(context: Context, token: String) = pushServiceHandler.updateToken(context, token)

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


    fun init(
        context: Context,
        configuration: MindboxConfiguration
    ) {
        runCatching {
            initComponents(context)

            val validatedConfiguration = validateConfiguration(configuration)

            mindboxScope.launch {
                if (MindboxPreferences.isFirstInitialize) {
                    firstInitialization(context, validatedConfiguration)
                    val isTrackVisitNotSent = MindboxInternalCore::lifecycleManager.isInitialized
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

                if (!MindboxInternalCore::lifecycleManager.isInitialized) {
                    val activity = context as? Activity
                    val isApplicationResumed = applicationLifecycle.currentState == RESUMED
                    if (isApplicationResumed && activity == null) {
                        MindboxLogger.e(
                            this@MindboxInternalCore,
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

    fun onNewIntent(intent: Intent?) = runCatching {
        if (MindboxInternalCore::lifecycleManager.isInitialized) {
            lifecycleManager.onNewIntent(intent)
        }
    }.logOnException()

    fun setLogLevel(level: Level) {
        MindboxLogger.level = level
    }

    @Deprecated("Used Mindbox.executeAsyncOperation with OperationBodyRequestBase")
    fun <T : OperationBody> executeAsyncOperation(
        context: Context,
        operationSystemName: String,
        operationBody: T
    ) = asyncOperation(context, operationSystemName, operationBody)

    fun <T : OperationBodyRequestBase> executeAsyncOperation(
        context: Context,
        operationSystemName: String,
        operationBody: T
    ) = asyncOperation(context, operationSystemName, operationBody)

    fun executeAsyncOperation(
        context: Context,
        operationSystemName: String,
        operationBodyJson: String
    ) = asyncOperation(context, operationSystemName, operationBodyJson)

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

    fun <T : OperationBodyRequestBase, V : OperationResponseBase> executeSyncOperation(
        context: Context,
        operationSystemName: String,
        operationBody: T,
        classOfV: Class<V>,
        onSuccess: (V) -> Unit,
        onError: (MindboxError) -> Unit
    ) {
        if (validateOperationAndInitializeComponents(context, operationSystemName)) {
            mindboxScope.launch {
                MindboxEventManager.syncOperation(
                    context = context,
                    name = operationSystemName,
                    body = operationBody,
                    classOfV = classOfV,
                    onSuccess = onSuccess,
                    onError = onError
                )
            }
        }
    }

    fun executeSyncOperation(
        context: Context,
        operationSystemName: String,
        operationBodyJson: String,
        onSuccess: (String) -> Unit,
        onError: (MindboxError) -> Unit
    ) {
        if (validateOperationAndInitializeComponents(context, operationSystemName)) {
            mindboxScope.launch {
                MindboxEventManager.syncOperation(
                    context = context,
                    name = operationSystemName,
                    bodyJson = operationBodyJson,
                    onSuccess = onSuccess,
                    onError = onError
                )
            }
        }
    }

    fun handleRemoteMessage(
        context: Context,
        message: RemoteMessage?,
        channelId: String,
        channelName: String,
        @DrawableRes pushSmallIcon: Int,
        defaultActivity: Class<out Activity>,
        channelDescription: String? = null,
        activities: Map<String, Class<out Activity>>? = null
    ): Boolean {
        val remoteMessage = FirebaseRemoteMessageTransformer.transform(message) ?: return false
        return PushNotificationManager.handleRemoteMessage(
            context = context,
            remoteMessage = remoteMessage,
            channelId = channelId,
            channelName = channelName,
            pushSmallIcon = pushSmallIcon,
            channelDescription = channelDescription,
            activities = activities,
            defaultActivity = defaultActivity
        )
    }

    fun getUrlFromPushIntent(intent: Intent?): String? = intent?.let {
        PushNotificationManager.getUrlFromPushIntent(intent)
    }

    internal fun initComponents(context: Context) {
        SharedPreferencesManager.with(context)
        DbManager.init(context)
        pushServiceHandler.initService(context)
    }

    private fun validateConfiguration(configuration: MindboxConfiguration): MindboxConfiguration {
        val validationErrors = SdkValidation.validateConfiguration(
            domain = configuration.domain,
            endpointId = configuration.endpointId,
            previousDeviceUUID = configuration.previousDeviceUUID,
            previousInstallationId = configuration.previousInstallationId
        )

        return if (validationErrors.isEmpty()) {
            configuration
        } else {
            if (validationErrors.any(SdkValidation.Error::critical)) {
                throw InitializeMindboxException(validationErrors.toString())
            }
            MindboxLogger.e(this, "Invalid configuration parameters found: $validationErrors")
            val isDeviceIdError = validationErrors.contains(SdkValidation.Error.INVALID_DEVICE_ID)
            val isInstallationIdError = validationErrors.contains(SdkValidation.Error.INVALID_INSTALLATION_ID)
            configuration.copy(
                previousDeviceUUID = if (isDeviceIdError) "" else configuration.previousDeviceUUID,
                previousInstallationId = if (isInstallationIdError) "" else configuration.previousInstallationId
            )
        }
    }

    private fun <T> asyncOperation(
        context: Context,
        operationSystemName: String,
        operationBody: T
    ) = runCatching {
        asyncOperation(
            context,
            operationSystemName,
            MindboxEventManager.operationBodyJson(operationBody)
        )
    }.logOnException()

    private fun asyncOperation(
        context: Context,
        operationSystemName: String,
        operationBodyJson: String
    ) {
        if (validateOperationAndInitializeComponents(context, operationSystemName)) {
            MindboxEventManager.asyncOperation(context, operationSystemName, operationBodyJson)
        }
    }

    private fun validateOperationAndInitializeComponents(
        context: Context,
        operationSystemName: String
    ) = runCatching {
        if (operationSystemName.matches(OPERATION_NAME_REGEX.toRegex())) {
            initComponents(context)
        } else {
            MindboxLogger.w(
                this,
                "Operation name is incorrect. It should contain only latin letters, number, '-' or '.' and length from 1 to 250."
            )
        }
        true
    }.returnOnException { false }

    private suspend fun initDeviceId(context: Context): String {
        val adid = mindboxScope.async { pushServiceHandler.getAdsIdentification(context) }
        return adid.await()
    }

    private suspend fun firstInitialization(context: Context, configuration: MindboxConfiguration) {
        runCatching {
            val pushToken = withContext(mindboxScope.coroutineContext) {
                pushServiceHandler.registerToken()
            }

            val isNotificationEnabled = PushNotificationManager.isNotificationsEnabled(context)
            val deviceUuid = initDeviceId(context)
            val instanceId = generateRandomUuid()

            DbManager.saveConfigurations(Configuration(configuration))

            val isTokenAvailable = !pushToken.isNullOrEmpty()
            val initData = InitData(
                token = pushToken ?: "",
                isTokenAvailable = isTokenAvailable,
                installationId = configuration.previousInstallationId,
                externalDeviceUUID = configuration.previousDeviceUUID,
                isNotificationsEnabled = isNotificationEnabled,
                subscribe = configuration.subscribeCustomerIfCreated,
                instanceId = instanceId
            )

            MindboxEventManager.appInstalled(context, initData, configuration.shouldCreateCustomer)

            MindboxPreferences.deviceUuid = deviceUuid
            MindboxPreferences.pushToken = pushToken
            MindboxPreferences.isNotificationEnabled = isNotificationEnabled
            MindboxPreferences.instanceId = instanceId
            MindboxPreferences.isFirstInitialize = false

            deliverDeviceUuid(deviceUuid)
            pushServiceHandler.deliverToken(pushToken)
        }.logOnException()
    }

    internal suspend fun updateAppInfo(context: Context, token: String? = null) {
        runCatching {

            val pushToken = token
                ?: withContext(mindboxScope.coroutineContext) { pushServiceHandler.registerToken() }

            val isTokenAvailable = !pushToken.isNullOrEmpty()

            val isNotificationEnabled = PushNotificationManager.isNotificationsEnabled(context)

            if ((isTokenAvailable && pushToken != MindboxPreferences.pushToken) || isNotificationEnabled != MindboxPreferences.isNotificationEnabled) {

                val initData = UpdateData(
                    token = pushToken ?: MindboxPreferences.pushToken ?: "",
                    isTokenAvailable = isTokenAvailable,
                    isNotificationsEnabled = isNotificationEnabled,
                    instanceId = MindboxPreferences.instanceId,
                    version = MindboxPreferences.infoUpdatedVersion
                )

                MindboxEventManager.appInfoUpdate(context, initData)

                MindboxPreferences.isNotificationEnabled = isNotificationEnabled
                MindboxPreferences.pushToken = pushToken
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

    private fun generateRandomUuid() = UUID.randomUUID().toString()

}
