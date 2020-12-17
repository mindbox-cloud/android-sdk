package cloud.mindbox.mobile_sdk

import android.content.Context
import com.orhanobut.hawk.Hawk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
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
        mindboxScope.launch {
            if (MindboxPreferences.isFirstInitialize) {
                firstInitialize(context, callback)
                MindboxPreferences.isFirstInitialize = false
            } else {
                secondaryInitialize()
            }
        }
    }

    fun getDeviceUuid(): String? {
        return MindboxPreferences.userAdid
    }

    fun getFirebaseToken(): String? {
        return MindboxPreferences.firebaseToken
    }

    private suspend fun firstInitialize(context: Context, callback: (String?, String?) -> Unit) {
        val firebaseToken = mindboxScope.async { IdentifierManager.getFirebaseToken() }
        val adid = mindboxScope.async { IdentifierManager.getAdsIdentification(context) }

        registerClient(firebaseToken.await(), adid.await(), callback)
    }

    private suspend fun secondaryInitialize() {

    }

    private fun registerClient(
        firebaseToken: String?,
        deviceUuid: String?,
        callback: (String?, String?) -> Unit
    ) {
        callback.invoke(firebaseToken, deviceUuid)
    }

    fun release() {
        context = null
        mindboxJob.cancel()
    }
}