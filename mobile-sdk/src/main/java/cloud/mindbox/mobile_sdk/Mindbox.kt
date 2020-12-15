package cloud.mindbox.mobile_sdk

import android.content.Context
import com.orhanobut.hawk.Hawk

object Mindbox {

    private var context: Context? = null

    fun init(context: Context, configuration: Configuration) {
        this.context = context
        initializeSdk(context, configuration)
    }

    private fun initializeSdk(context: Context, configuration: Configuration) {
        Hawk.init(context).build()
        configuration.registerFirebaseToken()
    }

    fun getDeviceUuid(onResult: (String?) -> Unit) {
        if (context == null) {
            Logger.e(this, "Mindbox SDK is not initialized")
            onResult.invoke(null)
        } else {
            Configuration().getDeviceUuid(context!!) { deviceUuid ->
                onResult.invoke(deviceUuid)
            }
        }
    }

    fun getFirebaseToken(onResult: (String?) -> Unit) {
        Configuration().getFirebaseToken { token ->
            onResult.invoke(token)
        }
    }

    fun release() {
        context = null
    }
}