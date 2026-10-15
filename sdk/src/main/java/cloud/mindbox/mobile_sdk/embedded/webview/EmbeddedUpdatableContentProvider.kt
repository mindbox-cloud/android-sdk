package cloud.mindbox.mobile_sdk.embedded.webview

import androidx.annotation.MainThread
import cloud.mindbox.mobile_sdk.embedded.EmbeddedContentProvider
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.models.Milliseconds

internal enum class DataPushOutcome {
    CONFIRMED,
    REFUSED,
    ABANDONED,
}

/**
 * An updatable provider can refresh its content in place over the bridge (`initDataUpdated`)
 * without recreating the webview — used when a new config keeps the same winner but changes
 * its params.
 **/
internal interface EmbeddedUpdatableContentProvider : EmbeddedContentProvider {

    @MainThread
    fun updateParams(params: Map<String, String>, onResult: (DataPushOutcome) -> Unit)

    @MainThread
    fun refreshForSession(
        params: Map<String, String>,
        sessionEpoch: Long,
        selectionTime: Milliseconds,
        onResult: (DataPushOutcome) -> Unit,
    )

    @get:MainThread
    val isDataPushAbandoned: Boolean
        get() = false

    @MainThread
    fun scheduleSessionRefresh(sessionEpoch: Long, selectionTime: Milliseconds)

    @MainThread
    fun confirmForSession(sessionEpoch: Long)

    @MainThread
    fun timeToDisplaySoFar(sessionEpoch: Long): Milliseconds?

    @MainThread
    fun withholdShow(isWithheld: Boolean)

    @get:MainThread
    val rendersNothing: Boolean

    var onRendersNothing: (() -> Unit)?

    fun refreshMetricsSnapshot(frequency: Frequency, tags: Map<String, String>?)
}
