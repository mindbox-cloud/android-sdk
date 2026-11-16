package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.SdkValidation
import cloud.mindbox.mobile_sdk.fromJson
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.models.TrackingId
import cloud.mindbox.mobile_sdk.pushes.PushConverter
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.pushes.TrackingIdResult
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.sha256
import cloud.mindbox.mobile_sdk.utils.SingleInitDelegate
import cloud.mindbox.mobile_sdk.utils.awaitAllWithTimeout
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

private const val READ_TRACKING_IDS_TIMEOUT = 5000L
private const val INVALIDATED_SENT_MARK = "invalidated"
private const val SENT_TYPES_SEPARATOR = ","
private const val SENT_STATE_SEPARATOR = "|"
private const val SET_IN_CLEAR_PREFIX = "["
private val sentStateLock = Any()

internal fun trackingIdTypesOf(
    configured: SingleInitDelegate<PushConverter>,
    live: List<PushServiceHandler>,
): Set<String>? {
    val passed = configured.assigned
    return (passed.orEmpty().filterIsInstance<PushServiceHandler>() + live)
        .takeIf { it.isNotEmpty() || passed != null }
        ?.mapNotNullTo(mutableSetOf()) { it.trackingIdType }
}

internal interface TrackingIdsResolver {

    suspend fun resolve(context: Context): List<TrackingId>?

    fun hasChanged(trackingIds: List<TrackingId>): Boolean

    fun markSent(trackingIds: List<TrackingId>)

    fun invalidateSent()

    fun resetSent()
}

internal class TrackingIdsResolverImpl(
    private val pushServiceHandlers: () -> List<PushServiceHandler> = { Mindbox.pushServiceHandlers },
    private val configuredTrackingIdTypes: () -> Set<String>? = { Mindbox.configuredTrackingIdTypes },
    private val scope: () -> CoroutineScope = { Mindbox.mindboxScope },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMillis: Long = READ_TRACKING_IDS_TIMEOUT,
) : TrackingIdsResolver {

    private val gson = Gson()

    override suspend fun resolve(context: Context): List<TrackingId>? {
        if (!MindboxPreferences.shouldCollectTrackingIds) return emptyList()
        anonymizerDomain()?.let { domain ->
            mindboxLogI("Tracking ids are not collected: operations go through operationsDomain $domain")
            return null
        }
        if (!hasTrackingHost()) return null

        val readings = pushServiceHandlers().associateBy { handler ->
            scope().async(ioDispatcher) { handler.tryGetTrackingId(context) }
        }
        val results = readings.keys.awaitAllWithTimeout(
            timeMillis = timeoutMillis,
            onCancel = { reading ->
                val handler = readings.getValue(reading)
                mindboxLogI("Timed out reading ${handler.trackingIdType} from ${handler.notificationProvider}")
            })

        if (results.size != readings.size || results.any { it is TrackingIdResult.Failed }) {
            mindboxLogI("Tracking ids could not be read, the operation goes without them")
            return null
        }
        val unreadSentTypes = results.unreadSentTypes()
        if (unreadSentTypes.isNotEmpty()) {
            mindboxLogI("Providers of the sent ${unreadSentTypes.joinToString()} are not ready, the operation goes without tracking ids")
            return null
        }
        return results
            .filterIsInstance<TrackingIdResult.Success>()
            .map { it.trackingId }
            .sortedBy { it.type }
            .distinctBy { it.type }
    }

    override fun hasChanged(trackingIds: List<TrackingId>): Boolean {
        val sent = sentState() ?: return trackingIds.isNotEmpty()
        return trackingIds.fingerprint() != sent.digest
    }

    override fun markSent(trackingIds: List<TrackingId>): Unit = loggingRunCatching {
        synchronized(sentStateLock) {
            storeSentState(digest = trackingIds.fingerprint(), types = trackingIds.map { it.type })
        }
        mindboxLogI("Sent tracking ids: ${trackingIds.joinToString { it.type }.ifEmpty { "none" }}")
    }

    override fun invalidateSent(): Unit = synchronized(sentStateLock) {
        storeSentState(digest = INVALIDATED_SENT_MARK, types = sentState()?.types.orEmpty())
    }

    override fun resetSent(): Unit = synchronized(sentStateLock) {
        MindboxPreferences.lastSentTrackingIds = ""
    }

    private class SentState(val digest: String, val types: Set<String>)

    private fun sentState(): SentState? {
        val stored = MindboxPreferences.lastSentTrackingIds
        return when {
            stored.isBlank() -> null
            stored.startsWith(SET_IN_CLEAR_PREFIX) -> SentState(digest = stored, types = typesOfSetStoredInClear(stored))
            else -> SentState(
                digest = stored.substringBefore(SENT_STATE_SEPARATOR),
                types = stored.substringAfter(SENT_STATE_SEPARATOR, missingDelimiterValue = "")
                    .split(SENT_TYPES_SEPARATOR)
                    .filterTo(mutableSetOf()) { it.isNotBlank() },
            )
        }
    }

    private fun storeSentState(digest: String, types: Collection<String>) {
        MindboxPreferences.lastSentTrackingIds = digest + SENT_STATE_SEPARATOR + types.joinToString(SENT_TYPES_SEPARATOR)
    }

    private fun List<TrackingIdResult>.unreadSentTypes(): List<String> {
        val configured = configuredTrackingIdTypes()
        val answered = mapNotNull { result ->
            when (result) {
                is TrackingIdResult.Success -> result.trackingId.type
                is TrackingIdResult.Denied -> result.type
                else -> null
            }
        }
        return sentState()?.types.orEmpty()
            .filter { type -> type !in answered && (configured == null || type in configured) }
    }

    private fun typesOfSetStoredInClear(stored: String): Set<String> =
        gson.fromJson<List<TrackingId>>(stored).getOrNull()
            ?.mapTo(mutableSetOf()) { it.type }
            .orEmpty()

    private fun hasTrackingHost(): Boolean {
        val domain = DbManager.getConfigurations()?.domain ?: return true
        if (SdkValidation.deriveTrackingHost(domain) != null) return true
        mindboxLogW("Tracking ids are not collected: no tracking host can be derived from $domain")
        return false
    }

    private fun List<TrackingId>.fingerprint(): String = gson.toJson(this).sha256()
}

internal fun anonymizerDomain(): String? =
    MindboxPreferences.operationsDomainFromConfig?.takeIf { it.isNotBlank() }
        ?: DbManager.getConfigurations()?.operationsDomain?.takeIf { it.isNotBlank() }
