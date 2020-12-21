package cloud.mindbox.mobile_sdk

import android.content.Context
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.managers.IdentifierManager
import cloud.mindbox.mobile_sdk.models.FullInitData
import cloud.mindbox.mobile_sdk.models.MindboxResponse
import cloud.mindbox.mobile_sdk.models.PartialInitData
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.orhanobut.hawk.Hawk
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.Main

object Mindbox {

    private var context: Context? = null
    private val mindboxJob = Job()
    private val mindboxScope = CoroutineScope(Default + mindboxJob)

    fun init(context: Context, configuration: Configuration, callback: (String?, String?) -> Unit) {
        this.context = context

        Hawk.init(context).build()

        mindboxScope.launch(Main) {
            callback.invoke(initDeviceId(), MindboxPreferences.installationId)
        }
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

    //todo validate fields
    fun registerSdk(
        context: Context,
        endpoint: String,
        deviceUuid: String,
        installationId: String,
        callback: (MindboxResponse) -> Unit
    ) {
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
        val adid = mindboxScope.async { IdentifierManager.getAdsIdentification(context) }
        setInstallationId(installationId)

        val deviceId = if (deviceUuid.isNotEmpty()) {
            deviceUuid
        } else {
            adid.await()
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
        val adid = mindboxScope.async { IdentifierManager.getAdsIdentification(context) }

        val deviceId = if (deviceUuid.isNotEmpty()) {
            deviceUuid
        } else {
            adid.await()
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