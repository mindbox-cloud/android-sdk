package cloud.mindbox.mindbox_rustore

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import cloud.mindbox.mobile_sdk.pushes.MindboxPushServiceDiscovery

// Implements Initializer directly: androidx.startup's own lint check crashes on a subclass of an
// abstract initializer. The catch keeps the app start alive if the core SDK is of another version.
internal class MindboxRuStoreInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        runCatching { MindboxPushServiceDiscovery.register(MindboxRuStore) }
            .onFailure { Log.e("Mindbox", "Failed to register the RuStore push service", it) }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
