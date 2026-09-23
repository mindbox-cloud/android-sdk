package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.TrackingId
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.pushes.TrackingIdResult
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

private const val READ_TRACKING_IDS_TIMEOUT = 5000L

internal interface TrackingIdsResolver {

    suspend fun resolve(context: Context): List<TrackingId>

    fun hasChanged(trackingIds: List<TrackingId>): Boolean

    fun markSent(trackingIds: List<TrackingId>)
}

internal class TrackingIdsResolverImpl(
    private val pushServiceHandlers: () -> List<PushServiceHandler> = { Mindbox.pushServiceHandlers },
    private val scope: CoroutineScope = Mindbox.mindboxScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMillis: Long = READ_TRACKING_IDS_TIMEOUT,
) : TrackingIdsResolver {

    private val gson = Gson()

    override suspend fun resolve(context: Context): List<TrackingId> {
        if (!MindboxPreferences.shouldCollectTrackingIds) return emptyList()

        val reading = scope.async(ioDispatcher) {
            pushServiceHandlers().map { handler -> handler.tryGetTrackingId(context) }
        }
        val results = withTimeoutOrNull(timeoutMillis.milliseconds) { reading.await() }
        if (results == null) {
            reading.cancel()
            mindboxLogI("Timed out reading tracking id providers, repeating the last reported set")
            return lastSent()
        }

        val ids = results.collectTrackingIds()
        if (ids.isNotEmpty()) return ids

        if (results.any { it is TrackingIdResult.Unavailable }) {
            mindboxLogI("Tracking id providers are unreachable, repeating the last reported set")
            return lastSent()
        }

        return emptyList()
    }

    override fun hasChanged(trackingIds: List<TrackingId>): Boolean = trackingIds != lastSent()

    override fun markSent(trackingIds: List<TrackingId>): Unit = loggingRunCatching {
        MindboxPreferences.lastSentTrackingIds = gson.toJson(trackingIds)
        mindboxLogI("Sent tracking ids: ${trackingIds.joinToString { it.type }.ifEmpty { "none" }}")
    }

    private fun lastSent(): List<TrackingId> = loggingRunCatching(defaultValue = emptyList()) {
        MindboxPreferences.lastSentTrackingIds
            .takeIf { it.isNotBlank() }
            ?.let { gson.fromJson(it, Array<TrackingId>::class.java)?.toList() }
            ?: emptyList()
    }

    private fun List<TrackingIdResult>.collectTrackingIds(): List<TrackingId> =
        filterIsInstance<TrackingIdResult.Success>()
            .map { it.trackingId }
            .sortedBy { it.type }
            .distinctBy { it.type }
}
