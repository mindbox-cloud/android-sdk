package cloud.mindbox.mobile_sdk

import android.content.Context
import com.orhanobut.hawk.Hawk

object Mindbox {

    private var context: Context? = null

    fun init(context: Context, configuration: Configuration) {
        this.context = context
        initializeSdk(context)
    }

    private fun initializeSdk(context: Context) {
        Hawk.init(context).build()
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

    fun release() {
        context = null
    }
}