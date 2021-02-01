package cloud.mindbox.mobile_sdk

import android.content.Context
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.EventManager
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

object Mindbox {

    private var context: Context? = null
    private val mindboxJob = Job()
    private val mindboxScope = CoroutineScope(Default + mindboxJob)

    fun getDeviceUuid() = MindboxPreferences.deviceUuid
    fun getFmsToken() = MindboxPreferences.firebaseTokenSaveDate
    fun getSdkVersion() = BuildConfig.VERSION_NAME

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
                        configuration.deviceUuid,
                        configuration.installationId
                    )
                }

        if (validationErrors.messages.isNotEmpty()) {
            throw InitializeMindboxException(validationErrors.messages.toString())
        }

        mindboxScope.launch {

            if (MindboxPreferences.isFirstInitialize) {
                MindboxPreferences.isNotificationEnabled =
                    IdentifierManager.isNotificationsEnabled(context)

                if (configuration.deviceUuid.trim().isEmpty()) {
                    configuration.deviceUuid = initDeviceId(context)
                } else {
                    configuration.deviceUuid.trim()
                }

                firstInitialization(configuration)
            } else {
                updateAppInfo(context)
            }
        }

        BackgroundWorkManager().start(context.applicationContext)
    }

    private suspend fun initDeviceId(context: Context): String {
        val adid = mindboxScope.async { IdentifierManager.getAdsIdentification(context) }
        return adid.await()
    }

    private fun setInstallationId(id: String) {
        if (id.isNotEmpty() && id != MindboxPreferences.installationId) {
            MindboxPreferences.installationId = id
        }
    }

    private suspend fun firstInitialization(configuration: Configuration) {
        val firebaseToken =
            withContext(mindboxScope.coroutineContext) { IdentifierManager.registerFirebaseToken() }
        MindboxPreferences.firebaseToken = firebaseToken
        setInstallationId(configuration.installationId)

        if (configuration.deviceUuid.isNotEmpty()) {
            MindboxPreferences.deviceUuid = configuration.deviceUuid
        }

        DbManager.saveConfigurations(configuration)

        val isTokenAvailable = !firebaseToken.isNullOrEmpty()
        val initData = FullInitData(
            firebaseToken ?: "",
            isTokenAvailable,
            MindboxPreferences.installationId ?: "",
            MindboxPreferences.isNotificationEnabled
        )

        EventManager.appInstalled(initData)
        MindboxPreferences.isFirstInitialize = false
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