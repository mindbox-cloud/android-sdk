package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.SdkValidation
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.models.TrackingId
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.pushes.TrackingIdResult
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.sha256
import cloud.mindbox.mobile_sdk.utils.awaitAllWithTimeout
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

private const val READ_TRACKING_IDS_TIMEOUT = 5000L

internal interface TrackingIdsResolver {

    suspend fun resolve(context: Context): List<TrackingId>?

    fun hasChanged(trackingIds: List<TrackingId>): Boolean

    fun markSent(trackingIds: List<TrackingId>)

    fun resetSent()
}

internal class TrackingIdsResolverImpl(
    private val pushServiceHandlers: () -> List<PushServiceHandler> = { Mindbox.pushServiceHandlers },
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
        var isTimedOut = false
        val results = readings.keys.awaitAllWithTimeout(
            timeMillis = timeoutMillis,
            onCancel = { reading ->
                isTimedOut = true
                val handler = readings.getValue(reading)
                mindboxLogI("Timed out reading ${handler.trackingIdType} from ${handler.notificationProvider}")
            })

        if (isTimedOut || results.any { it is TrackingIdResult.Failed }) {
            mindboxLogI("Tracking ids could not be read, the operation goes without them")
            return null
        }
        return results
            .filterIsInstance<TrackingIdResult.Success>()
            .map { it.trackingId }
            .sortedBy { it.type }
            .distinctBy { it.type }
    }

    override fun hasChanged(trackingIds: List<TrackingId>): Boolean {
        val lastSent = MindboxPreferences.lastSentTrackingIds
        return if (lastSent.isBlank()) trackingIds.isNotEmpty() else trackingIds.fingerprint() != lastSent
    }

    override fun markSent(trackingIds: List<TrackingId>): Unit = loggingRunCatching {
        MindboxPreferences.lastSentTrackingIds = trackingIds.fingerprint()
        mindboxLogI("Sent tracking ids: ${trackingIds.joinToString { it.type }.ifEmpty { "none" }}")
    }

    override fun resetSent() {
        MindboxPreferences.lastSentTrackingIds = ""
    }

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
