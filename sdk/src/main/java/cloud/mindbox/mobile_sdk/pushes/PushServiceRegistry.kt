package cloud.mindbox.mobile_sdk.pushes

import android.content.Context
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import cloud.mindbox.mobile_sdk.utils.MindboxUtils.Stopwatch
import cloud.mindbox.mobile_sdk.utils.awaitAllWithTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PushServiceRegistry(
    private val scope: () -> CoroutineScope,
) {

    private companion object {
        val INIT_TIMEOUT = Milliseconds(5000L)
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

    @Volatile
    private var explicitTags: List<String>? = null

    @Volatile
    private var active: List<Entry>? = null

    @Volatile
    private var isInitializationRequested = false

    val handlers: List<PushServiceHandler>
        get() = active.orEmpty().map { it.handler }

    val initializedHandlers: List<PushServiceHandler>
        get() = active.orEmpty().filter { it.isInitialized }.map { it.handler }

    val isSettled: Boolean
        get() = explicitTags != null

    fun isRegistered(tag: String): Boolean = active.orEmpty().any { it.service.tag == tag }

    fun registerExplicit(services: List<MindboxPushService>) {
        val tags = services.map { it.tag }.distinct()
        synchronized(lock) {
            val current = explicitTags
            if (tags.isEmpty()) {
                if (current == null) {
                    mindboxLogI("Push services list is empty: no push services are used")
                    explicitTags = emptyList()
                    updateActive()
                }
                return
            }
            if (!current.isNullOrEmpty() && current.toSet() != tags.toSet()) {
                mindboxLogW("Different push services lists passed: $current and $tags. All of them are used")
            }
            services.forEach(::entryFor)
            explicitTags = (current.orEmpty() + tags).distinct()
            updateActive()
        }
    }

    suspend fun initialize(context: Context): Boolean {
        isInitializationRequested = true
        if (active.isNullOrEmpty()) {
            mindboxLogI("No push services to initialize")
            return false
        }
        return initializeAvailable(context, isRecheckingUnavailable = true)
    }

    suspend fun initializePending(context: Context): Boolean =
        isInitializationRequested && initializeAvailable(context, isRecheckingUnavailable = false)

    fun markAvailable(tag: String): Unit = synchronized(lock) {
        active.orEmpty().firstOrNull { it.service.tag == tag }?.isAvailable = true
    }

    private suspend fun initializeAvailable(
        context: Context,
        isRecheckingUnavailable: Boolean,
    ): Boolean = initMutex.withLock {
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
        active = explicitTags?.mapNotNull { entries[it] }
    }
}
