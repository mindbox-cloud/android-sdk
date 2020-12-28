package cloud.mindbox.mobile_sdk

import android.content.Context
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.managers.IdentifierManager
import cloud.mindbox.mobile_sdk.models.FullInitData
import cloud.mindbox.mobile_sdk.models.MindboxResponse
import cloud.mindbox.mobile_sdk.models.PartialInitData
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.google.firebase.FirebaseApp
import com.orhanobut.hawk.Hawk
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.Main

object Mindbox {

    private var context: Context? = null
    private val mindboxJob = Job()
    private val mindboxScope = CoroutineScope(Default + mindboxJob)

    fun init(
        context: Context,
        endpoint: String,
        deviceUuid: String,
        installationId: String,
        callback: (MindboxResponse) -> Unit
    ) {
        this.context = context

        Hawk.init(context).build()
        FirebaseApp.initializeApp(context)

        mindboxScope.launch(Main) {
            val deviceId = if (deviceUuid.trim().isEmpty()) {
                initDeviceId()
            } else {
                deviceUuid.trim()
            }

            registerSdk(context, endpoint, deviceId ?: "", installationId, callback)
        }
    }

    fun getSdkData(onResult: (String, String, String) -> Unit) {
        onResult.invoke(
            MindboxPreferences.userAdid ?: "",
            MindboxPreferences.firebaseTokenSaveDate,
            "Some version - will be added later"
        )
    }

    internal suspend fun initDeviceId(): String? {
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
        endpoint: String,
        deviceUuid: String,
        installationId: String,
        callback: (MindboxResponse) -> Unit
    ) {
        val validationErrors =
            MindboxResponse.ValidationError().apply { validateFields(endpoint, deviceUuid) }

        if (validationErrors.messages.isNotEmpty()) {
            callback.invoke(validationErrors)
            return
        }

        mindboxScope.launch {
            if (MindboxPreferences.isFirstInitialize) {
                firstInitialize(context, endpoint, deviceUuid, installationId, callback)
            } else {
                secondaryInitialize(context, endpoint, deviceUuid, callback)
            }
        }
    }

    private suspend fun firstInitialize(
        context: Context,
        endpoint: String,
        deviceUuid: String,
        installationId: String,
        callback: (MindboxResponse) -> Unit
    ) {
        val firebaseToken =
            withContext(mindboxScope.coroutineContext) { IdentifierManager.getFirebaseToken() }
        val adid = withContext(mindboxScope.coroutineContext) {
            IdentifierManager.getAdsIdentification(context)
        }
        setInstallationId(installationId)

        val deviceId = if (deviceUuid.isNotEmpty()) {
            deviceUuid
        } else {
            adid
        }


        if (deviceUuid.isNotEmpty() && adid != deviceUuid) {
            MindboxPreferences.userAdid = deviceUuid
        }

        MindboxPreferences.isFirstInitialize = false

        val isTokenAvailable = !firebaseToken.isNullOrEmpty()
        val initData = FullInitData(
            firebaseToken ?: "",
            isTokenAvailable,
            MindboxPreferences.installationId ?: "",
            false //fixme
        )

        mindboxScope.launch {
            val result = GatewayManager.sendFirstInitialization(
                endpoint,
                deviceId ?: "",
                initData
            )

            callback.invoke(result)
        }
    }

    private suspend fun secondaryInitialize(
        context: Context,
        endpoint: String,
        deviceUuid: String,
        callback: (MindboxResponse) -> Unit
    ) {
        val firebaseToken =
            withContext(mindboxScope.coroutineContext) { IdentifierManager.getFirebaseToken() }
        val adid = withContext(mindboxScope.coroutineContext) {
            IdentifierManager.getAdsIdentification(context)
        }

        val deviceId = if (deviceUuid.isNotEmpty()) {
            deviceUuid
        } else {
            adid
        }

        if (deviceUuid.isNotEmpty() && adid != deviceUuid) {
            MindboxPreferences.userAdid = deviceUuid
        }

        val isTokenAvailable = !firebaseToken.isNullOrEmpty()
        val initData = PartialInitData(
            firebaseToken ?: "",
            isTokenAvailable,
            false //fixme
        )

        mindboxScope.launch {
            val result = GatewayManager.sendSecondInitialization(
                endpoint,
                deviceId ?: "",
                initData
            )

            callback.invoke(result)
        }
    }

    fun release() {
        context = null
        mindboxJob.cancel()
    }
}