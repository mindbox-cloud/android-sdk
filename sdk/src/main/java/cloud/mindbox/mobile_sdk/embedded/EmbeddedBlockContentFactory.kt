package cloud.mindbox.mobile_sdk.embedded

import android.content.Context
import androidx.annotation.MainThread
import cloud.mindbox.mobile_sdk.embedded.webview.EmbeddedBlockWebViewHolder
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.models.Milliseconds

internal object EmbeddedBlockContentFactory {

    @MainThread
    fun createProvider(
        context: Context,
        content: InAppType.Embedded,
        startTick: Milliseconds,
    ): EmbeddedContentProvider? {
        val layer = content.layers.filterIsInstance<Layer.WebViewLayer>().firstOrNull() ?: run {
            mindboxLogE("[EmbeddedBlock] Winner ${content.inAppId} has no webview layer")
            return null
        }
        return EmbeddedBlockWebViewHolder(
            inAppId = content.inAppId,
            placeSystemName = content.placeSystemName,
            layer = layer,
            context = context,
            frequency = content.frequency,
            tags = content.tags,
            startTick = startTick,
        )
    }
}
