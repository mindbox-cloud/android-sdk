package cloud.mindbox.mobile_sdk.embedded.webview

import android.view.View
import cloud.mindbox.mobile_sdk.embedded.EmbeddedBlockState
import cloud.mindbox.mobile_sdk.embedded.EmbeddedContentProvider
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW

internal class EmbeddedBlockWebViewProvider(
    private val page: EmbeddedBlockPage,
) : EmbeddedContentProvider {

    override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null

    override val contentView: View?
        get() = if (isReady) page.view else null

    private var isLoaded = false
    private var isActive = false
    private var isReady = false
    private var lastState: EmbeddedBlockState = EmbeddedBlockState.Loading

    init {
        page.onMessage = { message -> handle(message) }
        page.onPageError = { description ->
            // Latched even while paused: the system is free to kill the renderer of a backgrounded
            // WebView, and the next start must replay Failed — not a stale Ready over a dead page.
            mindboxLogW("[EmbeddedBlock] Block page failed: $description")
            isReady = false
            lastState = EmbeddedBlockState.Failed
            if (isActive) onStateChange?.invoke(EmbeddedBlockState.Failed)
            page.pause()
        }
    }

    override fun start() {
        isActive = true
        if (isLoaded) {
            page.resume()
            report(lastState)
            return
        }
        isLoaded = true
        isReady = false
        report(EmbeddedBlockState.Loading)
        page.load()
    }

    override fun pause() {
        isActive = false
        page.pause()
    }

    override fun release() {
        isActive = false
        isReady = false
        page.release()
    }

    private fun handle(message: TempEmbeddedBlockPageMessage) {
        if (!isActive) return

        when (message) {
            is TempEmbeddedBlockPageMessage.Ready -> applyHeight(message.heightCssPx)
            is TempEmbeddedBlockPageMessage.HeightChanged -> applyHeight(message.heightCssPx)
        }
    }

    private fun applyHeight(heightCssPx: Double) {
        // Zero height means the page worked and its targeting matched nothing — empty, not broken.
        if (heightCssPx <= 0) {
            mindboxLogI("[EmbeddedBlock] Block page reported zero height — nothing to show")
            isReady = false
            report(EmbeddedBlockState.Empty)
            page.pause()
            return
        }

        // A height no real block can have is a broken or hostile page: honoring it would hand one
        // JS message the power to blow up the host's measure pass.
        if (heightCssPx > MAX_BLOCK_HEIGHT_CSS_PX) {
            mindboxLogW("[EmbeddedBlock] Block page reported implausible height $heightCssPx, collapsing")
            isReady = false
            report(EmbeddedBlockState.Failed)
            page.pause()
            return
        }

        // The number is never applied — the host owns the block size; a plausible height only
        // proves the page rendered something real.
        isReady = true
        report(EmbeddedBlockState.Ready)
    }

    private fun report(state: EmbeddedBlockState) {
        lastState = state
        onStateChange?.invoke(state)
    }

    private companion object {
        private const val MAX_BLOCK_HEIGHT_CSS_PX = 4096.0
    }
}
