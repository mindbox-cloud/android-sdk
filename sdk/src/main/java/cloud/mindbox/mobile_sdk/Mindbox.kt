package cloud.mindbox.mobile_sdk

import android.content.Context
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.EventManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.managers.IdentifierManager
import cloud.mindbox.mobile_sdk.models.FullInitData
import cloud.mindbox.mobile_sdk.models.MindboxResponse
import cloud.mindbox.mobile_sdk.models.PartialInitData
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager
import com.google.firebase.FirebaseApp
import com.orhanobut.hawk.Hawk
import io.paperdb.Paper
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.Main

object Mindbox {

    private var context: Context? = null
    private val mindboxJob = Job()
    private val mindboxScope = CoroutineScope(Default + mindboxJob)

    fun init(
        context: Context,
        configuration: Configuration
    ) {
        this.context = context

        Hawk.init(context).build()
        Paper.init(context.applicationContext)
        FirebaseApp.initializeApp(context)

        val validationErrors =
            MindboxResponse.ValidationError()
                .apply {
                    validateFields(
                        configuration.domain,
                        configuration.endpoint,
                        configuration.deviceUuid
                    )
                }

        if (validationErrors.messages.isNotEmpty()) {
            throw InitializeMindboxException(validationErrors.toString())
        }

        mindboxScope.launch(Main) {
            val deviceId = if (configuration.deviceUuid.trim().isEmpty()) {
                initDeviceId()
            } else {
                configuration.deviceUuid.trim()
            }

            registerSdk(
                context,
                configuration,
                deviceId ?: "",
            )
        }

        BackgroundWorkManager().start(context.applicationContext)
    }

    fun getSdkData(onResult: (String, String, String) -> Unit) {
        onResult.invoke(
            MindboxPreferences.deviceUuid ?: "",
            MindboxPreferences.firebaseTokenSaveDate,
            "Some version - will be added later"
        )
    }

    private suspend fun initDeviceId(): String? {
        val adid = mindboxScope.async { IdentifierManager.getAdsIdentification(context) }
        return adid.await()
    }

    private fun setInstallationId(id: String) {
        if (id.isNotEmpty() && id != MindboxPreferences.installationId) {
            MindboxPreferences.installationId = id
        }
    }

    private fun registerSdk(
        context: Context,
        configuration: Configuration,
        deviceUuid: String
    ) {

        mindboxScope.launch {
            if (MindboxPreferences.isFirstInitialize) {
                MindboxPreferences.isNotificationEnabled =
                    IdentifierManager.isNotificationsEnabled(context)

                configuration.deviceUuid = deviceUuid

                firstInitialization(
                    context,
                    configuration,
                    deviceUuid,
                )
            } else {
                updateAppInfo(context)
            }
        }
    }

    private suspend fun firstInitialization(
        context: Context,
        configuration: Configuration,
        deviceUuid: String
    ) {
        val firebaseToken =
            withContext(mindboxScope.coroutineContext) { IdentifierManager.registerFirebaseToken() }
        MindboxPreferences.firebaseToken = firebaseToken
        setInstallationId(configuration.installationId)

        if (deviceUuid.isNotEmpty()) {
            MindboxPreferences.deviceUuid = deviceUuid
        }

        DbManager.saveConfigurations(configuration)

        val isTokenAvailable = !firebaseToken.isNullOrEmpty()
        val initData = FullInitData(
            firebaseToken ?: "",
            isTokenAvailable,
            MindboxPreferences.installationId ?: "",
            MindboxPreferences.isNotificationEnabled
        )

        mindboxScope.launch {
            GatewayManager.sendFirstInitialization(
                context,
                configuration,
                initData
            ) { result ->
                if (result is MindboxResponse.SuccessResponse<*>) {
                    MindboxPreferences.isFirstInitialize = false
                }
            }
        }
    }

    private suspend fun updateAppInfo(context: Context) {
        val firebaseToken =
            withContext(mindboxScope.coroutineContext) { IdentifierManager.registerFirebaseToken() }
        val isTokenAvailable = !firebaseToken.isNullOrEmpty()

        val isNotificationEnabled = IdentifierManager.isNotificationsEnabled(context)

        if ((isTokenAvailable && firebaseToken != MindboxPreferences.firebaseToken) || isNotificationEnabled != MindboxPreferences.isNotificationEnabled) {

            MindboxPreferences.isNotificationEnabled = isNotificationEnabled
            MindboxPreferences.firebaseToken = firebaseToken

            val initData = PartialInitData(
                firebaseToken ?: "",
                isTokenAvailable,
                MindboxPreferences.isNotificationEnabled
            )

            EventManager.appInfoUpdate(initData)
        }
    }

    fun release() {
        context = null
        mindboxJob.cancel()
    }
}