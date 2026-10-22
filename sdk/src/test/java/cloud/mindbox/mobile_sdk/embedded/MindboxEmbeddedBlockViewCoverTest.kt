package cloud.mindbox.mobile_sdk.embedded

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import cloud.mindbox.mobile_sdk.embedded.webview.DataPushOutcome
import cloud.mindbox.mobile_sdk.embedded.webview.EmbeddedUpdatableContentProvider
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.SessionEpoch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MindboxEmbeddedBlockViewCoverTest {

    private class Page(context: Activity) : EmbeddedUpdatableContentProvider {
        override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
        override val contentView: View = View(context)
        var touches = 0
        var releaseCount = 0
        var pushResult: ((DataPushOutcome) -> Unit)? = null

        init {
            contentView.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) touches++
                true
            }
        }

        override fun start() {
            onStateChange?.invoke(EmbeddedBlockState.Ready)
        }

        override fun pause() = Unit

        override fun release() {
            releaseCount++
        }

        override var hasRenderedLastData = true

        override fun updateParams(params: Map<String, String>, onResult: (DataPushOutcome) -> Unit) {
            hasRenderedLastData = false
            pushResult = onResult
        }

        override fun refreshForSession(
            params: Map<String, String>,
            sessionEpoch: SessionEpoch,
            selectionTime: Milliseconds,
            onResult: (DataPushOutcome) -> Unit,
        ) {
            hasRenderedLastData = false
            pushResult = onResult
        }

        override fun scheduleSessionRefresh(sessionEpoch: SessionEpoch, selectionTime: Milliseconds) = Unit

        override fun confirmForSession(sessionEpoch: SessionEpoch) = Unit

        override fun timeToDisplaySoFar(sessionEpoch: SessionEpoch): Milliseconds? = null

        override fun withholdShow(isWithheld: Boolean) = Unit

        override val rendersNothing = false

        override var onRendersNothing: (() -> Unit)? = null

        override fun refreshMetricsSnapshot(frequency: Frequency, tags: Map<String, String>?) = Unit

        fun render() {
            hasRenderedLastData = true
            onStateChange?.invoke(EmbeddedBlockState.Ready)
        }
    }

    private class HeldCrossfade : EmbeddedBlockRevealAnimation(areSystemAnimationsEnabled = { true }) {
        var onEnd: (() -> Unit)? = null
        var incoming: View? = null
        var outgoing: View? = null

        override fun crossfade(incoming: View, outgoing: View?, onEnd: () -> Unit): Animator {
            this.incoming = incoming
            this.outgoing = outgoing
            this.onEnd = onEnd
            incoming.alpha = 0f
            return ValueAnimator.ofFloat(0f, 1f)
        }

        fun finish() {
            incoming?.alpha = 1f
            outgoing?.alpha = 0f
            onEnd?.invoke()
        }
    }

    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val blocksRegistry = RecordingBlocksRegistry()
    private val pages = mutableListOf<Page>()
    private val listener = RecordingListener()
    private val content = InAppStub.getEmbedded()

    private fun buildView(crossfade: HeldCrossfade? = null): MindboxEmbeddedBlockView =
        MindboxEmbeddedBlockView(
            activity,
            null,
            "main-screen-top",
            loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.PLACEHOLDER,
            animatesReveal = crossfade != null,
            contentController = EmbeddedBlockContentController(
                placeSystemName = "main-screen-top",
                providerFactory = { _, _ -> Page(activity).also { page -> pages.add(page) } },
                blocksRegistry = { blocksRegistry },
                hostActivity = { activity },
            ),
            revealAnimation = crossfade ?: EmbeddedBlockRevealAnimation(),
        ).apply { setListener(listener) }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun attachAndLayOut(view: MindboxEmbeddedBlockView) {
        val host = FrameLayout(activity).apply { addView(view, BLOCK_WIDTH, BLOCK_HEIGHT) }
        activity.setContentView(host)
        idle()
        dispatchWindowVisibility(view, View.VISIBLE)
        idle()
        layOut(host)
    }

    private fun layOut(host: FrameLayout) {
        host.measure(
            MeasureSpec.makeMeasureSpec(BLOCK_WIDTH, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(BLOCK_HEIGHT, MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, BLOCK_WIDTH, BLOCK_HEIGHT)
    }

    private fun shownPage(crossfade: HeldCrossfade? = null): Pair<MindboxEmbeddedBlockView, Page> {
        val view = buildView(crossfade)
        attachAndLayOut(view)
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = SessionEpoch(0L)))
        idle()
        crossfade?.finish()
        layOut(view.parent as FrameLayout)
        return view to pages.single()
    }

    private fun coveredPage(crossfade: HeldCrossfade? = null): Pair<MindboxEmbeddedBlockView, Page> {
        val shown = shownPage(crossfade)
        blocksRegistry.endedSessions = setOf(SessionEpoch(0L))
        blocksRegistry.lastHandle?.onSessionRenewed()
        idle()
        return shown
    }

    private fun renderNewSessionData(page: Page) {
        blocksRegistry.lastHandle?.onContentResolved(content, placeAnswer(sessionEpoch = SessionEpoch(1L)))
        page.pushResult?.invoke(DataPushOutcome.CONFIRMED)
        page.render()
        idle()
    }

    private fun tap(view: View): Boolean {
        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
        val up = MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_UP, 10f, 10f, 0)
        try {
            val consumed = view.dispatchTouchEvent(down)
            view.dispatchTouchEvent(up)
            return consumed
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    @Test
    fun `a new session covers the shown page with the placeholder on top, keeping the page attached and visible`() {
        val (view, page) = coveredPage()

        assertEquals(2, view.childCount)
        assertSame(page.contentView, view.getChildAt(0))
        assertTrue(view.getChildAt(1) is EmbeddedBlockShimmerView)
        assertEquals(View.VISIBLE, page.contentView.visibility)
        assertEquals(0f, page.contentView.alpha)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, page.contentView.importantForAccessibility)
        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(0, page.releaseCount)
        assertEquals(listOf("load"), listener.events)
    }

    @Test
    fun `the placeholder covering the page fills the block like the page`() {
        val (view, page) = coveredPage()
        layOut(view.parent as FrameLayout)

        val cover = view.getChildAt(1)
        assertEquals(page.contentView.width, cover.width)
        assertEquals(page.contentView.height, cover.height)
        assertEquals(BLOCK_WIDTH, cover.width)
        assertEquals(BLOCK_HEIGHT, cover.height)
    }

    @Test
    fun `taps do not reach the covered page`() {
        val (view, page) = shownPage()
        assertTrue(tap(view))
        assertEquals(1, page.touches)
        blocksRegistry.endedSessions = setOf(SessionEpoch(0L))
        blocksRegistry.lastHandle?.onSessionRenewed()
        idle()

        assertTrue(tap(view))

        assertEquals(1, page.touches)
    }

    @Test
    fun `the page rendering the new session's data takes the placeholder off and gets taps again`() {
        val (view, page) = coveredPage()

        renderNewSessionData(page)

        assertEquals(1, view.childCount)
        assertSame(page.contentView, view.getChildAt(0))
        assertEquals(1f, page.contentView.alpha)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, page.contentView.importantForAccessibility)
        assertEquals(0, page.releaseCount)
        tap(view)
        assertEquals(1, page.touches)
        assertEquals(listOf("load"), listener.events)
    }

    @Test
    fun `a new session's nothing to show collapses the covered block`() {
        val (view, page) = coveredPage()

        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer(sessionEpoch = SessionEpoch(1L)))
        idle()

        assertEquals(View.GONE, view.visibility)
        assertEquals(0, view.childCount)
        assertEquals(1, page.releaseCount)
        assertEquals(listOf("load", "empty"), listener.events)
    }

    @Test
    fun `the host's placeholder set while the page is covered becomes the cover`() {
        val (view, page) = coveredPage()
        val custom = View(activity)

        view.setPlaceholderView(custom)

        assertEquals(2, view.childCount)
        assertSame(page.contentView, view.getChildAt(0))
        assertEquals(0f, page.contentView.alpha)
        assertSame(custom, view.getChildAt(1))

        view.setPlaceholderView(null)

        assertEquals(2, view.childCount)
        assertTrue(view.getChildAt(1) is EmbeddedBlockShimmerView)
    }

    @Test
    fun `a new session's other winner replaces the covered page under the placeholder`() {
        val (view, page) = coveredPage()

        blocksRegistry.lastHandle?.onContentResolved(content.copy(inAppId = "other-winner"), placeAnswer(sessionEpoch = SessionEpoch(1L)))
        idle()

        assertEquals(2, pages.size)
        assertEquals(1, page.releaseCount)
        assertEquals(1, view.childCount)
        assertSame(pages.last().contentView, view.getChildAt(0))
        assertFalse(view.getChildAt(0) is EmbeddedBlockShimmerView)
    }

    @Test
    fun `the placeholder fades out over the page that rendered the new session's data`() {
        val crossfade = HeldCrossfade()
        val (view, page) = coveredPage(crossfade)
        val cover = view.getChildAt(1)

        renderNewSessionData(page)

        assertSame(page.contentView, crossfade.incoming)
        assertSame(cover, crossfade.outgoing)
        assertEquals(2, view.childCount)
        tap(view)
        assertEquals(1, page.touches)

        crossfade.finish()

        assertEquals(1, view.childCount)
        assertSame(page.contentView, view.getChildAt(0))
        assertEquals(1f, page.contentView.alpha)
        assertEquals(1f, cover.alpha)
    }

    @Test
    fun `a block collapsing while the placeholder fades out leaves no placeholder behind`() {
        val crossfade = HeldCrossfade()
        val (view, page) = coveredPage(crossfade)
        renderNewSessionData(page)
        assertEquals(2, view.childCount)

        blocksRegistry.endedSessions = setOf(SessionEpoch(0L), SessionEpoch(1L))
        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer(sessionEpoch = SessionEpoch(2L)))
        idle()

        assertEquals(View.GONE, view.visibility)
        assertEquals(0, view.childCount)
        assertEquals(1, page.releaseCount)
    }

    private companion object {
        const val BLOCK_WIDTH = 500
        const val BLOCK_HEIGHT = 300
    }
}
