package cloud.mindbox.mobile_sdk.pushes

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.startup.Initializer
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching

/**
 * A class for internal sdk work only. Do not extend or use it.
 *
 * The base of the startup initializers of the push provider modules (`mindbox-firebase`,
 * `mindbox-huawei`, `mindbox-rustore`). Each registers its push service with the SDK at app
 * startup, so the integrator does not have to list it in `Mindbox.init`. A list passed to
 * `Mindbox.init` or `Mindbox.initPushServices` takes precedence.
 *
 * Registration only stores the service: nothing is initialized and no token is requested
 * until `Mindbox.init` is called.
 *
 * To opt out, remove the module's initializer in the app manifest, e.g. for Firebase:
 * ```xml
 * <provider
 *     android:name="androidx.startup.InitializationProvider"
 *     android:authorities="${applicationId}.androidx-startup"
 *     tools:node="merge">
 *     <meta-data
 *         android:name="cloud.mindbox.mindbox_firebase.MindboxFirebaseInitializer"
 *         tools:node="remove" />
 * </provider>
 * ```
 *
 * @param service the push service shipped by the module
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class MindboxPushServiceInitializer(
    private val service: MindboxPushService,
) : Initializer<Unit> {

    /**
     * Registers the module's push service. A failure is logged and never breaks the app start.
     */
    override fun create(context: Context) {
        loggingRunCatching { Mindbox.pushServiceRegistry.registerDiscovered(service) }
    }

    /**
     * No other initializer has to run first: registration does not touch the rest of the SDK.
     */
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
