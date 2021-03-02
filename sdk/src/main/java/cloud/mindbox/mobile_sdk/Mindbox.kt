package cloud.mindbox.mobile_sdk

import android.content.Context
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.IdentifierManager
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.InitData
import cloud.mindbox.mobile_sdk.models.UpdateData
import cloud.mindbox.mobile_sdk.models.ValidationError
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.google.firebase.FirebaseApp
import com.orhanobut.hawk.Hawk
import io.paperdb.Paper
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default

object Mindbox {

    private val mindboxJob = Job()
    private val mindboxScope = CoroutineScope(Default + mindboxJob)

    /**
     * Returns token of Firebase Messaging Service used by SDK
     */
    fun getFmsToken(): String? = runCatching { MindboxPreferences.firebaseToken }
        .returnOnException { null }

    /**
     * Returns date of FMS token saving
     */
    fun getFmsTokenSaveDate(): String = runCatching { MindboxPreferences.firebaseTokenSaveDate }
        .returnOnException { "" }

    /**
     * Returns SDK version
     */
    fun getSdkVersion(): String = runCatching { BuildConfig.VERSION_NAME }
        .returnOnException { "" }

    /**
     * Returns deviceUUID used by SDK
     *
     * @throws InitializeMindboxException when SDK isn't initialized
     */
    @Throws(InitializeMindboxException::class)
    fun getDeviceUuid(): String = runCatching { MindboxPreferences.deviceUuid }
        .returnOnException { null }
        ?: throw InitializeMindboxException("SDK was not initialized")

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
                            configuration.deviceUuid,
                            configuration.installationId
                        )
                    }

            validationErrors.messages
                ?: throw InitializeMindboxException(validationErrors.messages.toString())

            mindboxScope.launch {

                if (MindboxPreferences.isFirstInitialize) {

                    if (configuration.deviceUuid.trim().isEmpty()) {
                        configuration.deviceUuid = initDeviceId(context)
                    } else {
                        configuration.deviceUuid.trim()
                    }

                    firstInitialization(context, configuration)
                } else {
                    updateAppInfo(context)
                }
            }

            MindboxEventManager.sendEventsIfExist(context)
            context.schedulePeriodicService()
        }.logOnException()
    }

    internal fun initComponents(context: Context) {
        if (!Hawk.isBuilt()) Hawk.init(context).build()
        Paper.init(context)
        FirebaseApp.initializeApp(context)
    }

    private suspend fun initDeviceId(context: Context): String {
        return runCatching {
            val adid = mindboxScope.async { IdentifierManager.getAdsIdentification(context) }
            adid.await()
        }.returnOnException { "" }
    }

    private suspend fun firstInitialization(context: Context, configuration: MindboxConfiguration) {
        runCatching {
            val firebaseToken = withContext(mindboxScope.coroutineContext) {
                IdentifierManager.registerFirebaseToken()
            }

            val isNotificationEnabled = IdentifierManager.isNotificationsEnabled(context)

            DbManager.saveConfigurations(configuration)

            val isTokenAvailable = !firebaseToken.isNullOrEmpty()
            val initData = InitData(
                token = firebaseToken ?: "",
                isTokenAvailable = isTokenAvailable,
                installationId = configuration.installationId,
                isNotificationsEnabled = isNotificationEnabled,
                subscribe = configuration.subscribeCustomerIfCreated
            )

            MindboxEventManager.appInstalled(context, initData)

            MindboxPreferences.isFirstInitialize = false
            MindboxPreferences.firebaseToken = firebaseToken
            MindboxPreferences.installationId = configuration.installationId
            MindboxPreferences.deviceUuid = configuration.deviceUuid
            MindboxPreferences.isNotificationEnabled = isNotificationEnabled
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
                    token = firebaseToken ?: "",
                    isTokenAvailable = isTokenAvailable,
                    isNotificationsEnabled = isNotificationEnabled
                )

                MindboxEventManager.appInfoUpdate(context, initData)

                MindboxPreferences.isNotificationEnabled = isNotificationEnabled
                MindboxPreferences.firebaseToken = firebaseToken
            }
        }.logOnException()
    }
}