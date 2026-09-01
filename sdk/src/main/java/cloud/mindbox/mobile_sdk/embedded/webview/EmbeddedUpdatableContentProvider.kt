package cloud.mindbox.mobile_sdk.embedded.webview

import cloud.mindbox.mobile_sdk.embedded.EmbeddedContentProvider
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency

/**
 * An updatable provider can refresh its content in place over the bridge (`initDataUpdated`)
 * without recreating the webview — used when a new config keeps the same winner but changes
 * its params.
 **/
internal interface EmbeddedUpdatableContentProvider : EmbeddedContentProvider {

    fun updateParams(params: Map<String, String>, onResult: (Boolean) -> Unit)

    fun refreshMetricsSnapshot(frequency: Frequency, tags: Map<String, String>?)
}
