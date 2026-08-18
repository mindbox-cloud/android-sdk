package cloud.mindbox.mobile_sdk.embedded

import android.content.Context
import androidx.annotation.MainThread
import cloud.mindbox.mobile_sdk.embedded.webview.EmbeddedBlockWebViewHolder
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.models.Timestamp

internal object EmbeddedBlockContentFactory {

    @MainThread
    fun createProvider(
        context: Context,
        content: InAppType.Embedded,
        attemptStartedAt: Timestamp,
    ): EmbeddedContentProvider? {
        val layer = content.layers.filterIsInstance<Layer.WebViewLayer>().firstOrNull() ?: run {
            mindboxLogE("[EmbeddedBlock] Winner ${content.inAppId} has no webview layer")
            return null
        }
        return EmbeddedBlockWebViewHolder(
            inAppId = content.inAppId,
            layer = layer,
            context = context,
            attemptStartedAt = attemptStartedAt,
        )
    }
}
