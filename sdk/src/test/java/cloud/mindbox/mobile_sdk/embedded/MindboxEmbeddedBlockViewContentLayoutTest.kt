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
import java.io.Closeable

@RunWith(RobolectricTestRunner::class)
class MindboxEmbeddedBlockViewContentLayoutTest {

    private class FakeBlocksRegistry : EmbeddedBlocksRegistry {
        var lastHandle: EmbeddedBlockHandle? = null

        override fun register(placeSystemName: String, handle: EmbeddedBlockHandle): Closeable {
            lastHandle = handle
            return Closeable { lastHandle = null }
        }

        override fun onBlockAppeared(placeSystemName: String) = Unit

        override fun onBlockContentDropped(placeSystemName: String) = Unit

        override fun startListening() = Unit
    }

    /**
     * A host that answers a layout request the way Compose interop can: `AndroidViewsHandler`
     * never passes it up to `ViewRootImpl`, it translates it into a Compose remeasure request —
     * and that one is dropped while the node is being measured or waits in a LazyColumn's reuse
     * pool. Then no pass ever comes, which is what the block has to survive.
     */
    private class LayoutRequestSwallowingHost(context: Context) : FrameLayout(context) {
        override fun requestLayout() = Unit
    }

    /** A page that keeps loading until the test says it rendered — as one does off screen. */
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
    private val blocksRegistry = FakeBlocksRegistry()
    private var lastProvider: EmbeddedContentProvider? = null

    private fun buildView(
        provider: () -> EmbeddedContentProvider = { ReadyProvider(activity) },
    ): MindboxEmbeddedBlockView =
        MindboxEmbeddedBlockView(
            activity,
            null,
            "main-screen-top",
            contentController = EmbeddedBlockContentController(
                placeSystemName = "main-screen-top",
                providerFactory = { _, _ -> provider().also { lastProvider = it } },
                blocksRegistry = { blocksRegistry },
            ),
        )

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    /**
     * Attaches the block and lays the host out once — the only layout pass the test grants. What
     * happens to content that arrives afterwards must not depend on a second one.
     */
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

    /**
     * The corner case MOBILE-413 was actually caught on: a LazyColumn deactivates the item, so the
     * block leaves the window keeping the frame it had, and the page reports its content right
     * then. The reactivated node comes back without a remeasure of its own — nothing would ever
     * measure the content if the block waited for a pass.
     */
    @Test
    fun `content arriving while the block is out of the window still fills the frame it kept`() {
        val view = buildView(provider = { SilentProvider(activity) })
        val host = attachAndLayOut(view)
        // The page starts loading while the block is on screen…
        blocksRegistry.lastHandle?.onContentResolved(embeddedContent())
        idle()
        // …the item is deactivated, so the block leaves the window keeping its frame…
        host.removeView(view)
        idle()

        // …and only then the page reports what it rendered.
        (lastProvider as SilentProvider).reportReady()
        idle()

        val content = lastProvider?.contentView
        assertEquals(FRAME_WIDTH, content?.width)
        assertEquals(FRAME_HEIGHT, content?.height)
    }

    /**
     * The sequence MOBILE-413 was caught on: the list measures the item in one frame and places it
     * in a later one, and the page reports its content in between. A child added after the measure
     * has no measured size — `FrameLayout` would place it at 0×0 and eat its layout request along
     * the way, so the block has to measure it to the frame it is being laid out into.
     */
    @Test
    fun `content added between the measure and the layout of the block is measured too`() {
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

        // Measured, but not placed yet — the state the list leaves the item in between its own
        // measure and layout phases.
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

        // Out of the window and with the frame dropped, the way a fresh block starts out.
        host.removeView(view)
        view.layout(0, 0, 0, 0)
        idle()
        view.setPlaceholderView(content)
        idle()
        assertEquals(0, content.width)

        // The block has its frame back while the content is still unmeasured inside it — the state
        // a reactivated node comes back in. Restoring the frame is a layout of its own, so the
        // content is put back to nothing after it: what has to heal this is the reattachment.
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
