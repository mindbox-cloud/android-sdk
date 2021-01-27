package cloud.mindbox.mobile_sdk

import android.content.Context
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.managers.IdentifierManager
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.FullInitData
import cloud.mindbox.mobile_sdk.models.MindboxResponse
import cloud.mindbox.mobile_sdk.models.PartialInitData
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager
import com.google.firebase.FirebaseApp
import com.google.gson.Gson
import com.orhanobut.hawk.Hawk
import io.paperdb.Paper
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.Main
import java.util.*

object Mindbox {

    private var context: Context? = null
    private val mindboxJob = Job()
    private val mindboxScope = CoroutineScope(Default + mindboxJob)

    fun init(
        context: Context,
        configuration: Configuration,
        callback: (MindboxResponse) -> Unit
    ) {
        this.context = context

        Hawk.init(context).build()
        Paper.init(context.applicationContext)
        FirebaseApp.initializeApp(context)

        mindboxScope.launch(Main) {
            val deviceId = if (configuration.deviceId.trim().isEmpty()) {
                initDeviceId()
            } else {
                configuration.deviceId.trim()
            }

            registerSdk(
                context,
                configuration,
                deviceId ?: "",
                callback
            )
        }

        BackgroundWorkManager().start(context.applicationContext)
    }

    fun getSdkData(onResult: (String, String, String) -> Unit) {
        onResult.invoke(
            MindboxPreferences.deviceId ?: "",
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
        deviceUuid: String,
        callback: (MindboxResponse) -> Unit
    ) {
        val validationErrors =
            MindboxResponse.ValidationError()
                .apply { validateFields(configuration.domain, configuration.endpoint, deviceUuid) }

        if (validationErrors.messages.isNotEmpty()) {
            callback.invoke(validationErrors)
            return
        }

        mindboxScope.launch {
            if (MindboxPreferences.isFirstInitialize) {
                configuration.deviceId = deviceUuid

                firstInitialize(
                    context,
                    configuration,
                    deviceUuid,
                    callback
                )
            } else {
                configuration.deviceId = MindboxPreferences.deviceId ?: ""

                secondaryInitialize(
                    context,
                    configuration,
                    callback
                )
            }
        }
    }

    private suspend fun firstInitialize(
        context: Context,
        configuration: Configuration,
        deviceUuid: String,
        callback: (MindboxResponse) -> Unit
    ) {
        val firebaseToken =
            withContext(mindboxScope.coroutineContext) { IdentifierManager.getFirebaseToken() }
        setInstallationId(configuration.installationId)

        if (deviceUuid.isNotEmpty()) {
            MindboxPreferences.deviceId = deviceUuid
        }

        DbManager.saveConfigurations(configuration)

        val isTokenAvailable = !firebaseToken.isNullOrEmpty()
        val initData = FullInitData(
            firebaseToken ?: "",
            isTokenAvailable,
            MindboxPreferences.installationId ?: "",
            false //fixme
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
                callback.invoke(result)
            }
        }
    }

    private suspend fun secondaryInitialize(
        context: Context,
        configuration: Configuration,
        callback: (MindboxResponse) -> Unit
    ) {
        val firebaseToken =
            withContext(mindboxScope.coroutineContext) { IdentifierManager.getFirebaseToken() }

        val isTokenAvailable = !firebaseToken.isNullOrEmpty()
        val initData = PartialInitData(
            firebaseToken ?: "",
            isTokenAvailable,
            false //fixme
        )

        val gson = Gson()

        DbManager.addEventToStack(Event(
            UUID.randomUUID().toString(),
            -1,
            Date().time,
            gson.toJson(initData)
        ))
    }

    fun release() {
        context = null
        mindboxJob.cancel()
    }
}