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

/**
 * An updatable provider sends new data to its page in place over the bridge (`initDataUpdated`)
 * without recreating the webview — used when a new config keeps the same winner but changes its
 * params, and when a new session picks the same winner again. It also accounts the page's show
 * once per session: the session it confirms, whether the show is withheld, whether the page
 * renders nothing, and the frequency and tags the show is counted with.
 **/
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
