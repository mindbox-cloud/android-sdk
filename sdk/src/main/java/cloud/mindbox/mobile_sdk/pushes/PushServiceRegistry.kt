package cloud.mindbox.mobile_sdk.pushes

import android.content.Context
import android.content.pm.PackageManager
import cloud.mindbox.mobile_sdk.getApplicationInfoCompat
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.managers.trackingIdTypesOf
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import cloud.mindbox.mobile_sdk.utils.MindboxUtils.Stopwatch
import cloud.mindbox.mobile_sdk.utils.awaitAllWithTimeout
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PushServiceRegistry(
    private val scope: () -> CoroutineScope,
) {

    private companion object {
        val DISCOVERY_ORDER = listOf("FCM", "HCM", "RuStore")
        val INIT_TIMEOUT = Milliseconds(5000L)
        const val PROVIDER_META_DATA_PREFIX = "cloud.mindbox.push.provider."
    }

    private class Entry(val service: MindboxPushService) {
        val handler: PushServiceHandler by lazy {
            service.getServiceHandler(MindboxLoggerImpl, LoggingExceptionHandler)
        }

        @Volatile
        var isInitialized: Boolean = false

        @Volatile
        var isAvailable: Boolean? = null
    }

    private val lock = Any()
    private val initMutex = Mutex()
    private val entries = HashMap<String, Entry>()
    private var discoveredTags: List<String>? = null

    @Volatile
    private var explicitTags: List<String>? = null

    @Volatile
    private var active: List<Entry>? = null

    @Volatile
    private var isInitializationRequested = false

    internal val handlers: List<PushServiceHandler>
        get() = active.orEmpty().map { it.handler }

    internal val initializedHandlers: List<PushServiceHandler>
        get() = active.orEmpty().filter { it.isInitialized }.map { it.handler }

    internal val isSettled: Boolean
        get() = explicitTags != null || (isInitializationRequested && active != null)

    internal val trackingIdTypes: Set<String>?
        get() {
            val (services, isKnown) = synchronized(lock) { active.orEmpty() to isSettled }
            return trackingIdTypesOf(services.map { it.handler }, isKnown)
        }

    internal fun resolveHandlers(context: Context): List<PushServiceHandler> {
        discover(context)
        return handlers
    }

    internal fun isRegistered(context: Context, tag: String): Boolean {
        discover(context)
        return active.orEmpty().any { it.service.tag == tag }
    }

    internal fun registerExplicit(services: List<MindboxPushService>) {
        val tags = services.map { it.tag }.distinct()
        synchronized(lock) {
            val current = explicitTags
            if (tags.isEmpty()) return
            if (!current.isNullOrEmpty() && current.toSet() != tags.toSet()) {
                mindboxLogW("Different push services lists passed: $current and $tags. All of them are used")
            }
            val discovered = discoveredTags.orEmpty()
            if (current == null && discovered.isNotEmpty() && discovered.toSet() != tags.toSet()) {
                mindboxLogI("Push services $tags replace $discovered found in the manifest")
            }
            services.forEach(::entryFor)
            explicitTags = (current.orEmpty() + tags).distinct()
            updateActive()
        }
    }

    internal suspend fun initialize(context: Context): Boolean {
        val wasSettled = isSettled
        val isFirstRequest = !isInitializationRequested
        isInitializationRequested = true
        discover(context)
        if (active.isNullOrEmpty()) {
            mindboxLogI("No push services to initialize")
            return !wasSettled && isSettled
        }
        return initializeAvailable(context, isRecheckingUnavailable = true) || isFirstRequest
    }

    internal suspend fun initializePending(context: Context): Boolean =
        isInitializationRequested && initializeAvailable(context, isRecheckingUnavailable = false)

    internal fun markAvailable(tag: String): Unit = synchronized(lock) {
        active.orEmpty().firstOrNull { it.service.tag == tag }?.isAvailable = true
    }

    private fun discover(context: Context) {
        if (active != null) return
        synchronized(lock) {
            if (discoveredTags != null || explicitTags != null) return
            val services = loggingRunCatching(defaultValue = null) { readManifestServices(context) } ?: return
            val tags = services.map { it.tag }.distinct().sortedBy(::discoveryRank)
            services.forEach(::entryFor)
            discoveredTags = tags
            updateActive()
            mindboxLogI("Push services found in the manifest: ${tags.joinToString().ifEmpty { "none" }}")
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
            @Suppress("UnjustifiedReflection")
            Class.forName(className).getDeclaredField("INSTANCE").get(null) as? MindboxPushService
        }

    private suspend fun initializeAvailable(
        context: Context,
        isRecheckingUnavailable: Boolean,
    ): Boolean = initMutex.withLock {
        discover(context)
        val services = active.orEmpty()
        if (isRecheckingUnavailable && services.none { it.isInitialized }) {
            synchronized(lock) {
                services.forEach { if (it.isAvailable == false) it.isAvailable = null }
            }
        }
        val available = services
            .filterNot { it.isInitialized }
            .filter { it.checkAvailability(context) }
        if (available.isEmpty()) return@withLock false

        mindboxLogI("Initializing push services: ${available.joinToString { it.service.tag }}")
        Stopwatch.start(Stopwatch.INIT_PUSH_SERVICES)
        try {
            available.map { entry ->
                scope().async {
                    runCatching { entry.handler.initService(context) }
                        .onFailure { mindboxLogE("${entry.service.tag} failed to initialize", it) }
                }
            }.awaitAllWithTimeout(INIT_TIMEOUT.interval)
        } finally {
            available.forEach { it.isInitialized = true }
        }
        mindboxLogI("Push services initialized in " + Stopwatch.stop(Stopwatch.INIT_PUSH_SERVICES))
        true
    }

    private fun Entry.checkAvailability(context: Context): Boolean {
        isAvailable?.let { return it }
        val isServiceAvailable = handler.isServiceAvailable(context)
        return synchronized(lock) { isAvailable ?: isServiceAvailable.also { isAvailable = it } }
    }

    private fun entryFor(service: MindboxPushService): Entry {
        val existing = entries[service.tag]
        if (existing != null && existing.service === service) return existing
        return Entry(service).also { entries[service.tag] = it }
    }

    private fun updateActive() {
        active = (explicitTags ?: discoveredTags)?.mapNotNull { entries[it] }
    }

    private fun discoveryRank(tag: String): Int =
        DISCOVERY_ORDER.indexOf(tag).takeIf { it >= 0 } ?: Int.MAX_VALUE
}
