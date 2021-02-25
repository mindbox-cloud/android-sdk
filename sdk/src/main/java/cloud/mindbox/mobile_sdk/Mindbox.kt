package cloud.mindbox.mobile_sdk

import android.content.Context
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.EventManager
import cloud.mindbox.mobile_sdk.managers.IdentifierManager
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

    private var appContext: Context? = null
    private val mindboxJob = Job()
    private val mindboxScope = CoroutineScope(Default + mindboxJob)

    fun getFmsToken() = MindboxPreferences.firebaseToken
    fun getFmsTokenSaveDate() = MindboxPreferences.firebaseTokenSaveDate
    fun getSdkVersion() = BuildConfig.VERSION_NAME
    fun getDeviceUuid(): String = MindboxPreferences.deviceUuid
        ?: throw InitializeMindboxException("SDK was not initialized")

    fun updateFmsToken(token: String) {
        if (appContext != null && token.trim().isNotEmpty()) {
            mindboxScope.launch {
                updateAppInfo(appContext!!, token)
            }
        }
    }

    fun onPushReceived(context: Context, uniqKey: String) {

        if (!Hawk.isBuilt()) Hawk.init(context).build()
        Paper.init(context.applicationContext)
        FirebaseApp.initializeApp(context)

        EventManager.pushDelivered(context, uniqKey)

        if (!MindboxPreferences.isFirstInitialize) {
            mindboxScope.launch {
                updateAppInfo(context)
            }
        }
    }

    fun init(
        context: Context,
        configuration: Configuration
    ) {
        appContext = context

        Hawk.init(context).build()
        Paper.init(context.applicationContext)
        FirebaseApp.initializeApp(context)

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

        if (validationErrors.messages.isNotEmpty()) {
            throw InitializeMindboxException(validationErrors.messages.toString())
        }

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

        EventManager.sendEventsIfExist(context)
        context.schedulePeriodicService()
    }

    private suspend fun initDeviceId(context: Context): String {
        val adid = mindboxScope.async { IdentifierManager.getAdsIdentification(context) }
        return adid.await()
    }

    private suspend fun firstInitialization(context: Context, configuration: Configuration) {
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

        EventManager.appInstalled(context, initData)

        MindboxPreferences.isFirstInitialize = false
        MindboxPreferences.firebaseToken = firebaseToken
        MindboxPreferences.installationId = configuration.installationId
        MindboxPreferences.deviceUuid = configuration.deviceUuid
        MindboxPreferences.isNotificationEnabled = isNotificationEnabled
    }

    private suspend fun updateAppInfo(context: Context, token: String? = null) {

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

            EventManager.appInfoUpdate(context, initData)

            MindboxPreferences.isNotificationEnabled = isNotificationEnabled
            MindboxPreferences.firebaseToken = firebaseToken
        }
    }

    fun release() {
        appContext = null
        mindboxJob.cancel()
    }
}