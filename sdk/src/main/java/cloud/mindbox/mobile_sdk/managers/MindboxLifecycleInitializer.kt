package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.startup.Initializer
import cloud.mindbox.mobile_sdk.getCurrentProcessName
import cloud.mindbox.mobile_sdk.isMainProcess
import cloud.mindbox.mobile_sdk.logger.mindboxLogI

/**
 * Registers [LifecycleManager] at application startup via androidx.startup so that lifecycle
 * tracking begins before [cloud.mindbox.mobile_sdk.Mindbox.init] is called.
 *
 * Track-visit events are only dispatched after [cloud.mindbox.mobile_sdk.Mindbox.init] wires
 * the [LifecycleManager.onTrackVisitReady] callback.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class MindboxLifecycleInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        val currentProcessName = context.getCurrentProcessName()
        if (!context.isMainProcess(currentProcessName)) return

        mindboxLogI("LifecycleInitializer: Register LifecycleManager in startup initializer")
        LifecycleManager.register(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
