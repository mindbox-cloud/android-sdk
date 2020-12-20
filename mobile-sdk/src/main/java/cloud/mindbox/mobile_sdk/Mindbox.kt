package cloud.mindbox.mobile_sdk

import android.content.Context
import com.orhanobut.hawk.Hawk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

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

    fun getDeviceUuid(): String? {
        return MindboxPreferences.userAdid
    }

    fun getFirebaseToken(): String? {
        return MindboxPreferences.firebaseToken
    }

    fun setInstallationId(id: String) {
        if (id != MindboxPreferences.installationId) {
            MindboxPreferences.installationId = id
        }
    }

    fun registerSdl(context: Context) {
        mindboxScope.launch {
            if (MindboxPreferences.isFirstInitialize) {
                firstInitialize(context)
            } else {
                secondaryInitialize()
            }
        }
    }

    private suspend fun firstInitialize(context: Context) {
        val firebaseToken = mindboxScope.async { IdentifierManager.getFirebaseToken() }
        val adid = mindboxScope.async { IdentifierManager.getAdsIdentification(context) }

        registerClient(firebaseToken.await(), adid.await())
    }

    private suspend fun secondaryInitialize() {

    }

    private fun registerClient(
        firebaseToken: String?,
        deviceUuid: String?
    ) {
        MindboxPreferences.isFirstInitialize = false
    }

    fun release() {
        context = null
        mindboxJob.cancel()
    }
}