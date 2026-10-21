package cloud.mindbox.mobile_sdk.embedded

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.models.InAppStub
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MindboxEmbeddedBlockViewContentLayoutTest {

    private class LayoutRequestSwallowingHost(context: Context) : FrameLayout(context) {
        override fun requestLayout() = Unit
    }

    private class SilentProvider(context: Activity) : EmbeddedContentProvider {
        override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
        override val contentView: View = View(context)

        override fun start() = Unit

        override fun pause() = Unit

        override fun release() = Unit

        fun reportReady() {
            onStateChange?.invoke(EmbeddedBlockState.Ready)
        }
    }

    private class ReadyProvider(context: Activity) : EmbeddedContentProvider {
        override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
        override val contentView: View = View(context)

        override fun start() {
            onStateChange?.invoke(EmbeddedBlockState.Ready)
        }

        override fun pause() = Unit

        override fun release() = Unit
    }

    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val blocksRegistry = RecordingBlocksRegistry()
    private var lastProvider: EmbeddedContentProvider? = null

    private fun buildView(
        provider: () -> EmbeddedContentProvider = { ReadyProvider(activity) },
    ): MindboxEmbeddedBlockView =
        MindboxEmbeddedBlockView(
            activity,
            null,
            "main-screen-top",
            loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.PLACEHOLDER,
            contentController = EmbeddedBlockContentController(
                placeSystemName = "main-screen-top",
                providerFactory = { _, _ -> provider().also { lastProvider = it } },
                blocksRegistry = { blocksRegistry },
            ),
        )

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun attachAndLayOut(view: MindboxEmbeddedBlockView, padding: Int = 0): FrameLayout {
        val host = LayoutRequestSwallowingHost(activity).apply {
            addView(view, FRAME_WIDTH, FRAME_HEIGHT)
        }
        view.setPadding(padding, padding, padding, padding)
        activity.setContentView(host)
        idle()
        dispatchWindowVisibility(view, View.VISIBLE)
        idle()
        layOutOnce(host)
        return host
    }

    private fun layOutOnce(host: FrameLayout) {
        host.measure(
            MeasureSpec.makeMeasureSpec(FRAME_WIDTH, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(FRAME_HEIGHT, MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, FRAME_WIDTH, FRAME_HEIGHT)
    }

    @Test
    fun `content arriving after the block was laid out fills the frame without another host pass`() {
        val view = buildView()
        attachAndLayOut(view)

        blocksRegistry.lastHandle?.onContentResolved(embeddedContent())
        idle()

        val content = lastProvider?.contentView
        assertEquals(FRAME_WIDTH, content?.width)
        assertEquals(FRAME_HEIGHT, content?.height)
    }

    @Test
    fun `content laid out by the block itself honors the padding of the frame`() {
        val view = buildView()
        attachAndLayOut(view, padding = PADDING)

        blocksRegistry.lastHandle?.onContentResolved(embeddedContent())
        idle()

        val content = lastProvider?.contentView
        assertEquals(FRAME_WIDTH - 2 * PADDING, content?.width)
        assertEquals(FRAME_HEIGHT - 2 * PADDING, content?.height)
        assertEquals(PADDING, content?.left)
        assertEquals(PADDING, content?.top)
    }

    @Test
    fun `content arriving while a deactivated LazyColumn item keeps the block out of the window still fills the frame it kept`() {
        val view = buildView(provider = { SilentProvider(activity) })
        val host = attachAndLayOut(view)
        blocksRegistry.lastHandle?.onContentResolved(embeddedContent())
        idle()
        host.removeView(view)
        idle()

        (lastProvider as SilentProvider).reportReady()
        idle()

        val content = lastProvider?.contentView
        assertEquals(FRAME_WIDTH, content?.width)
        assertEquals(FRAME_HEIGHT, content?.height)
    }

    @Test
    fun `content added between the measure and the layout of the block is measured to the frame, as FrameLayout would not`() {
        val view = buildView(provider = { SilentProvider(activity) })
        val host = LayoutRequestSwallowingHost(activity).apply {
            addView(view, FRAME_WIDTH, FRAME_HEIGHT)
        }
        activity.setContentView(host)
        idle()
        dispatchWindowVisibility(view, View.VISIBLE)
        idle()
        blocksRegistry.lastHandle?.onContentResolved(embeddedContent())
        idle()

        view.layout(0, 0, 0, 0)
        host.measure(
            MeasureSpec.makeMeasureSpec(FRAME_WIDTH, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(FRAME_HEIGHT, MeasureSpec.EXACTLY),
        )
        (lastProvider as SilentProvider).reportReady()
        idle()
        host.layout(0, 0, FRAME_WIDTH, FRAME_HEIGHT)

        val content = lastProvider?.contentView
        assertEquals(FRAME_WIDTH, content?.width)
        assertEquals(FRAME_HEIGHT, content?.height)
    }

    @Test
    fun `content added without a frame is laid out when the block returns to the window`() {
        val view = buildView()
        val host = attachAndLayOut(view)
        val content = View(activity)

        host.removeView(view)
        view.layout(0, 0, 0, 0)
        idle()
        view.setPlaceholderView(content)
        idle()
        assertEquals(0, content.width)

        view.layout(0, 0, FRAME_WIDTH, FRAME_HEIGHT)
        content.layout(0, 0, 0, 0)
        host.addView(view, FRAME_WIDTH, FRAME_HEIGHT)
        idle()

        assertEquals(FRAME_WIDTH, content.width)
        assertEquals(FRAME_HEIGHT, content.height)
    }

    @Test
    fun `padding given after the content is shown moves it without another host pass`() {
        val view = buildView()
        attachAndLayOut(view)
        blocksRegistry.lastHandle?.onContentResolved(embeddedContent())
        idle()
        val content = lastProvider?.contentView

        view.setPadding(PADDING, PADDING, PADDING, PADDING)
        idle()

        assertEquals(FRAME_WIDTH - 2 * PADDING, content?.width)
        assertEquals(FRAME_HEIGHT - 2 * PADDING, content?.height)
        assertEquals(PADDING, content?.left)
        assertEquals(PADDING, content?.top)
    }

    @Test
    fun `a placeholder swapped in after the layout fills the frame too`() {
        val view = buildView()
        attachAndLayOut(view)
        val placeholder = View(activity)

        view.setPlaceholderView(placeholder)
        idle()

        assertEquals(FRAME_WIDTH, placeholder.width)
        assertEquals(FRAME_HEIGHT, placeholder.height)
    }

    private fun embeddedContent(): InAppType.Embedded = InAppStub.getEmbedded()

    private companion object {
        const val FRAME_WIDTH = 500
        const val FRAME_HEIGHT = 300
        const val PADDING = 20
    }
}
