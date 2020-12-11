package cloud.mindbox.mobile_sdk

import android.content.Context
import com.orhanobut.hawk.Hawk

object Mindbox {

    fun init(context: Context, configuration: Configuration) {
        initializeSdk(context)
    }

    private fun initializeSdk(context: Context) {
        Hawk.init(context).build()
    }

    fun release() {
    }
}