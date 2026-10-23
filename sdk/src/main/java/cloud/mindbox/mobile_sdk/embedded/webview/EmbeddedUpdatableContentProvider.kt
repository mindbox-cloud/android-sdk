package cloud.mindbox.mobile_sdk.embedded.webview

import androidx.annotation.MainThread
import cloud.mindbox.mobile_sdk.embedded.EmbeddedContentProvider
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.SessionEpoch

internal enum class DataPushOutcome {
    CONFIRMED,
    REFUSED,
    ABANDONED,
}

internal interface EmbeddedUpdatableContentProvider : EmbeddedContentProvider {

    @MainThread
    fun updateParams(params: Map<String, String>, onResult: (DataPushOutcome) -> Unit)

    @MainThread
    fun refreshForSession(
        params: Map<String, String>,
        sessionEpoch: SessionEpoch,
        selectionTime: Milliseconds,
        onResult: (DataPushOutcome) -> Unit,
    )

    @get:MainThread
    val isDataPushAbandoned: Boolean
        get() = false

    @get:MainThread
    val hasRenderedLastData: Boolean

    @MainThread
    fun setCovered(isCovered: Boolean)

    @MainThread
    fun scheduleSessionRefresh(sessionEpoch: SessionEpoch, selectionTime: Milliseconds)

    @MainThread
    fun confirmForSession(sessionEpoch: SessionEpoch)

    @MainThread
    fun timeToDisplaySoFar(sessionEpoch: SessionEpoch): Milliseconds?

    @MainThread
    fun withholdShow(isWithheld: Boolean)

    @get:MainThread
    val rendersNothing: Boolean

    var onRendersNothing: (() -> Unit)?

    fun refreshMetricsSnapshot(frequency: Frequency, tags: Map<String, String>?)
}
