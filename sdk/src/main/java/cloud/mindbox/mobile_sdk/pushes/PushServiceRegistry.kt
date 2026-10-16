package cloud.mindbox.mobile_sdk.pushes

import android.content.Context
import android.content.pm.PackageManager
import cloud.mindbox.mobile_sdk.getApplicationInfoCompat
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import cloud.mindbox.mobile_sdk.utils.MindboxUtils.Stopwatch
import cloud.mindbox.mobile_sdk.utils.awaitAllWithTimeout
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The push services the SDK works with in this process.
 *
 * Services come from two sources. Provider modules register themselves at app startup
 * ([registerDiscovered]); the integrator may pass a list to `Mindbox.init` or
 * `Mindbox.initPushServices` ([registerExplicit]). Once a non-empty list is passed, it alone
 * decides which services are used and in what order, and lists from several calls are merged.
 * An empty list changes nothing. If App Startup did not run at all, the services are taken from
 * the manifest meta-data instead ([discoverFromManifestIfNeeded]).
 *
 * Registration is synchronous and cheap. Services are initialized later, by [initializeAvailable].
 */
internal class PushServiceRegistry(
    private val scope: () -> CoroutineScope,
) {

    private companion object {
        // Discovered services follow this order whatever order their modules registered in.
        // The first available service gives the device id on the first launch.
        val DISCOVERY_ORDER = listOf("FCM", "HCM", "RuStore")
        const val INIT_TIMEOUT = 5000L
        const val PROVIDER_META_DATA_PREFIX = "cloud.mindbox.push.provider."
    }

    private class Entry(val service: MindboxPushService) {
        val handler: PushServiceHandler by lazy {
            service.getServiceHandler(MindboxLoggerImpl, LoggingExceptionHandler)
        }

        @Volatile
        var isInitialized: Boolean = false

        @Volatile
        var isAvailabilityChecked: Boolean = false
    }

    private val lock = Any()
    private val initMutex = Mutex()
    private val entries = HashMap<String, Entry>()
    private var discoveredTags: List<String> = emptyList()
    private var explicitTags: List<String>? = null
    private var isManifestChecked = false

    @Volatile
    private var active: List<Entry> = emptyList()

    /** True once androidx.startup ran the SDK's initializers, so provider modules had their chance to register. */
    @Volatile
    var isAppStartupRan: Boolean = false
        private set

    fun markAppStartupRan() {
        isAppStartupRan = true
    }

    /** Every service in use, whether it is available on the device or not. */
    val handlers: List<PushServiceHandler>
        get() = active.map { it.handler }

    /** Services in use that were available and went through initialization. */
    val initializedHandlers: List<PushServiceHandler>
        get() = active.filter { it.isInitialized }.map { it.handler }

    fun isRegistered(tag: String): Boolean = active.any { it.service.tag == tag }

    fun registerDiscovered(service: MindboxPushService) {
        synchronized(lock) {
            if (service.tag in discoveredTags) return
            entryFor(service)
            discoveredTags = (discoveredTags + service.tag).sortedBy(::discoveryRank)
            updateActive()
        }
        mindboxLogI("Push service ${service.tag} discovered")
    }

    fun registerExplicit(services: List<MindboxPushService>) {
        val tags = services.map { it.tag }.distinct()
        if (tags.isEmpty()) {
            // Flutter and React Native always pass an empty list to init, meaning "no list here".
            mindboxLogI("Push services list is empty, keeping the services in use")
            return
        }
        synchronized(lock) {
            val current = explicitTags
            if (current != null && current.toSet() != tags.toSet()) {
                mindboxLogW("Different push services lists passed: $current and $tags. All of them are used")
            }
            if (current == null && discoveredTags.isNotEmpty() && discoveredTags.toSet() != tags.toSet()) {
                mindboxLogI("Push services $tags replace discovered $discoveredTags")
            }
            services.forEach(::entryFor)
            explicitTags = (current.orEmpty() + tags).distinct()
            updateActive()
        }
    }

    /**
     * Registers the push services listed in the manifest meta-data `cloud.mindbox.push.provider.*`
     * when App Startup did not run (the app removed or replaced androidx.startup's
     * InitializationProvider) and nothing else gave the services. Checks the manifest once.
     *
     * The meta-data lives outside InitializationProvider, so it survives its removal. While App
     * Startup runs, the manifest is not read: a module whose initializer the app removed stays off.
     */
    fun discoverFromManifestIfNeeded(context: Context) {
        if (isAppStartupRan || active.isNotEmpty()) return
        synchronized(lock) {
            if (isManifestChecked || explicitTags != null || discoveredTags.isNotEmpty()) return
            isManifestChecked = true
            val services = loggingRunCatching(defaultValue = emptyList()) { readManifestServices(context) }
            if (services.isEmpty()) return
            mindboxLogW(
                "Push services were not registered at app startup: androidx.startup InitializationProvider " +
                    "seems to be removed from the manifest. Using the services from the manifest: " +
                    "${services.joinToString { it.tag }}. Pass them to Mindbox.init or Mindbox.initPushServices " +
                    "to stop this warning.",
            )
            services.forEach(::registerDiscovered)
        }
    }

    private fun readManifestServices(context: Context): List<MindboxPushService> {
        val metaData = context.packageManager
            .getApplicationInfoCompat(context, PackageManager.GET_META_DATA)
            .metaData
            ?: return emptyList()
        return metaData.keySet()
            .filter { it.startsWith(PROVIDER_META_DATA_PREFIX) }
            .mapNotNull { metaData.getString(it) }
            .mapNotNull(::loadPushService)
    }

    private fun loadPushService(className: String): MindboxPushService? =
        loggingRunCatching(defaultValue = null) {
            // Provider objects are kept by name with all members by the consumer rules of their modules
            // (proguard-firebase.pro, proguard-huawei.pro, proguard-rustore.pro).
            @Suppress("UnjustifiedReflection")
            Class.forName(className).getDeclaredField("INSTANCE").get(null) as? MindboxPushService
        }

    /**
     * Initializes the services in use that have not been initialized yet and are available now.
     * A service unavailable now is checked again on the next call.
     *
     * @return true if at least one service was initialized by this call.
     */
    suspend fun initializeAvailable(context: Context): Boolean = initMutex.withLock {
        val available = active
            .filterNot { it.isInitialized }
            .filter { it.checkAvailability(context) }
        if (available.isEmpty()) return@withLock false

        mindboxLogI("Initializing push services: ${available.joinToString { it.service.tag }}")
        Stopwatch.start(Stopwatch.INIT_PUSH_SERVICES)
        available.map { entry ->
            scope().async {
                runCatching { entry.handler.initService(context) }
                    .onFailure { mindboxLogE("${entry.service.tag} failed to initialize", it) }
            }
        }.awaitAllWithTimeout(INIT_TIMEOUT)
        // A service that failed or timed out stays in use, as it always did: its token is still requested.
        available.forEach { it.isInitialized = true }
        mindboxLogI("Push services initialized in " + Stopwatch.stop(Stopwatch.INIT_PUSH_SERVICES))
        true
    }

    private fun Entry.checkAvailability(context: Context): Boolean =
        handler.isServiceAvailable(context, logUnavailable = !isAvailabilityChecked)
            .also { isAvailabilityChecked = true }

    private fun entryFor(service: MindboxPushService): Entry {
        val existing = entries[service.tag]
        if (existing != null && existing.service === service) return existing
        return Entry(service).also { entries[service.tag] = it }
    }

    private fun updateActive() {
        active = (explicitTags ?: discoveredTags).mapNotNull { entries[it] }
    }

    private fun discoveryRank(tag: String): Int =
        DISCOVERY_ORDER.indexOf(tag).takeIf { it >= 0 } ?: Int.MAX_VALUE
}
