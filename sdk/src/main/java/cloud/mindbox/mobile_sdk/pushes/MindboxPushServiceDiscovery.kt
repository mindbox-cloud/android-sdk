package cloud.mindbox.mobile_sdk.pushes

import androidx.annotation.RestrictTo
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching

/**
 * An object for internal sdk work only. Do not use it.
 *
 * The push provider modules (`mindbox-firebase`, `mindbox-huawei`, `mindbox-rustore`) register
 * their push service here from their androidx.startup initializers, so the integrator does not
 * have to list it in `Mindbox.init`. A list passed to `Mindbox.init` or `Mindbox.initPushServices`
 * takes precedence.
 *
 * If the app disables App Startup altogether (removes or replaces InitializationProvider), the
 * initializers do not run. The SDK then takes the services from the `cloud.mindbox.push.provider.*`
 * meta-data of the provider modules and logs a warning.
 *
 * To opt out of one module, remove its initializer in the app manifest, e.g. for Firebase:
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
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object MindboxPushServiceDiscovery {

    /**
     * Registers [service] found in the app. Only stores it: nothing is initialized and no token
     * is requested until `Mindbox.init` is called. A failure is logged and never breaks the app start.
     *
     * @param service the push service shipped by the calling module
     */
    public fun register(service: MindboxPushService) {
        loggingRunCatching { Mindbox.pushServiceRegistry.registerDiscovered(service) }
    }
}
