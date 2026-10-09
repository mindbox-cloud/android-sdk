package cloud.mindbox.mobile_sdk.embedded.webview

import androidx.annotation.MainThread
import cloud.mindbox.mobile_sdk.embedded.EmbeddedContentProvider
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.models.Milliseconds

/**
 * An updatable provider can refresh its content in place over the bridge (`initDataUpdated`)
 * without recreating the webview.
 **/
internal interface EmbeddedUpdatableContentProvider : EmbeddedContentProvider {

    fun updateParams(params: Map<String, String>, onResult: (Boolean) -> Unit)

    @MainThread
    fun refreshForSession(
        params: Map<String, String>,
        sessionEpoch: Long,
        selectionStartTick: Milliseconds,
        onResult: (Boolean) -> Unit,
    )

    @MainThread
    fun confirmForSession(sessionEpoch: Long)

    @MainThread
    fun withholdShow(isWithheld: Boolean)

    fun refreshMetricsSnapshot(frequency: Frequency, tags: Map<String, String>?)
}
