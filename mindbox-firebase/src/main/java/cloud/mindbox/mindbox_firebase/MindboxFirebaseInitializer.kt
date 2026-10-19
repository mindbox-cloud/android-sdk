package cloud.mindbox.mindbox_firebase

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import cloud.mindbox.mobile_sdk.pushes.MindboxPushServiceDiscovery

// Implements Initializer directly: androidx.startup's own lint check crashes on a subclass of an
// abstract initializer. The catch keeps the app start alive if the core SDK is of another version.
internal class MindboxFirebaseInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        runCatching { MindboxPushServiceDiscovery.register(MindboxFirebase) }
            .onFailure { Log.e("Mindbox", "Failed to register the Firebase push service", it) }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
