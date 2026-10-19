package cloud.mindbox.mindbox_huawei

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import cloud.mindbox.mobile_sdk.pushes.MindboxPushServiceDiscovery

// Implements Initializer directly: androidx.startup's own lint check crashes on a subclass of an
// abstract initializer. The catch keeps the app start alive if the core SDK is of another version.
internal class MindboxHuaweiInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        runCatching { MindboxPushServiceDiscovery.register(MindboxHuawei) }
            .onFailure { Log.e("Mindbox", "Failed to register the Huawei push service", it) }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
