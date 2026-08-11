package cloud.mindbox.mobile_sdk.embedded

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.Milliseconds
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class MindboxEmbeddedBlockViewTest {

    /** A pure recorder — the show/hide behavior belongs to the view itself. */
    private class RecordingListener : MindboxEmbeddedBlockListener {
        val events = mutableListOf<String>()

        override fun onLoad(view: MindboxEmbeddedBlockView) {
            events.add("load")
        }

        override fun onFail(view: MindboxEmbeddedBlockView) {
            events.add("fail")
        }
    }

    /**
     * Mirrors the real provider contract: contentView is gone the moment the content is not
     * Ready, start() reports a state synchronously (Loading on a fresh load, the current state
     * on a resume), pause() keeps the content.
     */
    private class FakeProvider : EmbeddedContentProvider {
        override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
        var readyView: View? = null
        override val contentView: View?
            get() = if (isReady) readyView else null
        private var isReady = false
        private var lastState: EmbeddedBlockState = EmbeddedBlockState.Loading
        var startCount = 0
        var pauseCount = 0
        var releaseCount = 0

        override fun start() {
            startCount++
            onStateChange?.invoke(lastState)
        }

        override fun pause() {
            pauseCount++
        }

        override fun release() {
            releaseCount++
        }

        fun report(state: EmbeddedBlockState) {
            lastState = state
            isReady = state is EmbeddedBlockState.Ready
            onStateChange?.invoke(state)
        }
    }

    private val activityController: ActivityController<Activity> =
        Robolectric.buildActivity(Activity::class.java).setup()
    private val activity: Activity = activityController.get()
    private val provider = FakeProvider()
    private val listener = RecordingListener()

    /** Built the way a host builds it: configured first, put on screen afterwards. */
    private fun blockView(timeout: Milliseconds = Milliseconds(10_000L)): MindboxEmbeddedBlockView =
        MindboxEmbeddedBlockView(
            activity,
            null,
            "test-place",
            EmbeddedBlockContentController(
                resolveFactory = { EmbeddedContentResolution.Content(provider) },
                placeSystemName = "test-place",
                readyTimeout = timeout,
            ),
        )

    private fun attach(view: MindboxEmbeddedBlockView) {
        activity.setContentView(
            // The host owns the size: an explicit fixed height, as the contract demands.
            LinearLayout(activity).apply { addView(view, 500, 300) },
        )
        showWindow(view)
    }

    /**
     * Robolectric leaves every window at GONE, so the block — which starts its content only once
     * the window is actually visible — would never start. On a device the framework dispatches
     * this down the tree right after the attach; here the test stands in for it.
     */
    private fun showWindow(view: MindboxEmbeddedBlockView) {
        shadowOf(Looper.getMainLooper()).idle()
        dispatchWindowVisibility(view, View.VISIBLE)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun attachedView(timeout: Milliseconds = Milliseconds(10_000L)): MindboxEmbeddedBlockView =
        blockView(timeout).also { attach(it) }

    /** The single state child the container is currently showing. */
    private fun shownChild(view: MindboxEmbeddedBlockView): View? {
        assertTrue("one state child at a time", view.childCount <= 1)
        return if (view.childCount == 1) view.getChildAt(0) else null
    }

    @Test
    fun `exposes the place it was created for`() {
        assertEquals("test-place", attachedView().placeSystemName)
    }

    @Test
    fun `starts the content on attach and pauses it on detach`() {
        val view = attachedView()

        assertEquals(1, provider.startCount)

        (view.parent as LinearLayout).removeView(view)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, provider.pauseCount)
    }

    @Test
    fun `shows the default placeholder while loading`() {
        val view = attachedView()

        // The default placeholder is an SDK-internal view — anything shown means the frame is
        // not empty while loading.
        assertTrue(shownChild(view) != null)
    }

    @Test
    fun `a custom placeholder replaces the default one`() {
        val custom = View(activity)
        val view = blockView()

        view.setPlaceholderView(custom)
        attach(view)

        assertSame(custom, shownChild(view))
    }

    @Test
    fun `a placeholder set while loading swaps in right away`() {
        val custom = View(activity)
        val view = attachedView()

        view.setPlaceholderView(custom)

        // The block is loading right now — the host should not have to wait for the next state
        // change to see its own placeholder.
        assertSame(custom, shownChild(view))
    }

    @Test
    fun `dropping the placeholder while loading brings the default placeholder back`() {
        val view = attachedView()
        val custom = View(activity)
        view.setPlaceholderView(custom)

        view.setPlaceholderView(null)

        val child = shownChild(view)
        assertTrue(child != null && child !== custom)
    }

    @Test
    fun `a placeholder set after the content arrived does not disturb it`() {
        val view = attachedView()
        val content = View(activity)
        provider.readyView = content
        provider.report(EmbeddedBlockState.Ready)

        view.setPlaceholderView(View(activity))

        // Only the loading frame belongs to the placeholder; live content is not touched.
        assertSame(content, shownChild(view))
    }

    @Test
    fun `an error view set after the block collapsed waits for the next outcome`() {
        val view = attachedView()
        provider.report(EmbeddedBlockState.Failed)
        shadowOf(Looper.getMainLooper()).idle()

        view.setErrorView(View(activity))
        shadowOf(Looper.getMainLooper()).idle()

        // Deliberate: re-expanding a collapsed block under the host's fingers would shove the
        // rest of the screen around. The next reload picks the error view up.
        assertEquals(View.GONE, view.visibility)
        assertNull(shownChild(view))
    }

    @Test
    fun `shows the content when ready`() {
        val view = attachedView()
        val content = View(activity)
        provider.readyView = content
        provider.report(EmbeddedBlockState.Ready)

        assertSame(content, shownChild(view))
    }

    @Test
    fun `keeps the content child through a detach and shows it again on re-attach`() {
        val view = attachedView()
        val content = View(activity)
        provider.readyView = content
        provider.report(EmbeddedBlockState.Ready)
        val parent = view.parent as LinearLayout

        parent.removeView(view)
        shadowOf(Looper.getMainLooper()).idle()

        // The content is this block's own — a pause keeps it, nothing to rebuild later.
        assertSame(view, content.parent)

        parent.addView(view, 500, 300)
        showWindow(view)

        // The provider replayed Ready on resume — the same content is on screen again.
        assertSame(content, shownChild(view))
    }

    @Test
    fun `a failure with no error view empties the frame`() {
        val view = attachedView()
        provider.readyView = View(activity)
        provider.report(EmbeddedBlockState.Ready)
        provider.report(EmbeddedBlockState.Failed)

        // The block collapses, so it has nothing left to draw — including the dead content.
        assertNull(shownChild(view))
    }

    @Test
    fun `a custom error view replaces the empty frame`() {
        val custom = View(activity)
        val view = blockView()
        view.setErrorView(custom)
        attach(view)

        provider.report(EmbeddedBlockState.Failed)

        assertSame(custom, shownChild(view))
    }

    @Test
    fun `empty content clears the frame`() {
        val view = attachedView()
        provider.report(EmbeddedBlockState.Empty)

        // Nothing to show is not an error: no error view, just an empty transparent frame
        // (which the block hides anyway).
        assertNull(shownChild(view))
    }

    @Test
    fun `ready content without a view falls back to the error state`() {
        val view = attachedView()
        provider.readyView = null
        provider.report(EmbeddedBlockState.Ready)
        shadowOf(Looper.getMainLooper()).idle()

        // Nothing to attach — the frame must not stay on the placeholder forever.
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `by default the block hides itself when there is nothing to show`() {
        val view = attachedView()
        assertEquals(View.VISIBLE, view.visibility)

        provider.report(EmbeddedBlockState.Empty)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `by default the block hides itself when the content fails`() {
        val view = attachedView()

        provider.report(EmbeddedBlockState.Failed)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `a custom error view keeps the block visible on failure by default`() {
        val custom = View(activity)
        val view = blockView()
        view.setErrorView(custom)
        attach(view)

        provider.report(EmbeddedBlockState.Failed)
        shadowOf(Looper.getMainLooper()).idle()

        // Setting an error view is a request to show the failure (the iOS semantics):
        // no listener override needed — the default onFail keeps the block in place.
        assertEquals(View.VISIBLE, view.visibility)
        assertSame(custom, shownChild(view))
    }

    @Test
    fun `a custom error view keeps the block visible on an empty place too`() {
        val custom = View(activity)
        val view = blockView()
        view.setErrorView(custom)
        attach(view)

        provider.report(EmbeddedBlockState.Empty)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.VISIBLE, view.visibility)
        assertSame(custom, shownChild(view))
    }

    @Test
    fun `a fresh loading shows a previously hidden block again`() {
        val view = attachedView()
        provider.report(EmbeddedBlockState.Failed)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(View.GONE, view.visibility)

        // A new session reload: the content goes back to Loading — the block must come back.
        provider.report(EmbeddedBlockState.Loading)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.VISIBLE, view.visibility)
    }

    @Test
    fun `the callback runs after the block applied its behavior so the host wins`() {
        val view = attachedView()
        view.setListener(object : MindboxEmbeddedBlockListener {
            override fun onFail(view: MindboxEmbeddedBlockView) {
                // The block already hid itself — a host that wants the error visible anyway
                // simply re-shows it here.
                view.visibility = View.VISIBLE
            }
        })

        provider.report(EmbeddedBlockState.Failed)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.VISIBLE, view.visibility)
    }

    @Test
    fun `delivers onLoad when the content arrives`() {
        val view = attachedView()
        view.setListener(listener)
        shadowOf(Looper.getMainLooper()).idle()
        provider.readyView = View(activity)
        provider.report(EmbeddedBlockState.Ready)
        shadowOf(Looper.getMainLooper()).idle()

        // Loading is not a public outcome — only the load lands in the listener.
        assertEquals(listOf("load"), listener.events)
    }

    @Test
    fun `an empty place is reported to the host as a place without content`() {
        val view = attachedView()
        view.setListener(listener)
        shadowOf(Looper.getMainLooper()).idle()
        provider.report(EmbeddedBlockState.Empty)
        shadowOf(Looper.getMainLooper()).idle()

        // The host asks one question — did the place get content? — and gets one answer either
        // way; an empty place is a normal outcome, not a breakage.
        assertEquals(listOf("fail"), listener.events)
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `late listener still receives the current state`() {
        val view = attachedView()
        provider.report(EmbeddedBlockState.Failed)
        shadowOf(Looper.getMainLooper()).idle()

        view.setListener(listener)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("fail"), listener.events)
    }

    @Test
    fun `re-registering the same listener does not replay the outcome`() {
        val view = attachedView()
        view.setListener(listener)
        provider.report(EmbeddedBlockState.Failed)
        shadowOf(Looper.getMainLooper()).idle()

        // A recycled row rebinds its listener on every pass. The host rebuilds its layout on the
        // outcome, and rebuilding the layout rebinds the listener — replaying here spins that.
        view.setListener(listener)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("fail"), listener.events)
    }

    @Test
    fun `a different listener still receives the current outcome`() {
        val view = attachedView()
        view.setListener(listener)
        provider.report(EmbeddedBlockState.Failed)
        shadowOf(Looper.getMainLooper()).idle()

        val second = RecordingListener()
        view.setListener(second)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("fail"), second.events)
    }

    @Test
    fun `dropping the listener stops the callbacks without touching the content`() {
        val view = attachedView()
        view.setListener(listener)
        shadowOf(Looper.getMainLooper()).idle()

        view.setListener(null)
        provider.readyView = View(activity)
        provider.report(EmbeddedBlockState.Ready)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(listener.events.isEmpty())
        assertSame(provider.readyView, shownChild(view))
    }

    @Test
    fun `the same state is not delivered twice`() {
        val view = attachedView()
        view.setListener(listener)
        provider.report(EmbeddedBlockState.Failed)
        shadowOf(Looper.getMainLooper()).idle()
        provider.report(EmbeddedBlockState.Failed)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("fail"), listener.events)
    }

    @Test
    fun `a changed state is delivered again`() {
        val view = attachedView()
        view.setListener(listener)
        provider.readyView = View(activity)
        provider.report(EmbeddedBlockState.Failed)
        shadowOf(Looper.getMainLooper()).idle()
        provider.report(EmbeddedBlockState.Ready)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("fail", "load"), listener.events)
    }

    @Test
    fun `silent content times out into the error state`() {
        val view = attachedView(timeout = Milliseconds(1_000L))
        view.setListener(listener)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_100L))

        // Pause comes before the Failed state so the provider cannot resurrect the block.
        assertEquals(1, provider.pauseCount)
        assertEquals(listOf("fail"), listener.events)
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `content that resolved in time does not time out`() {
        val view = attachedView(timeout = Milliseconds(1_000L))
        view.setListener(listener)
        shadowOf(Looper.getMainLooper()).idle()
        provider.readyView = View(activity)
        provider.report(EmbeddedBlockState.Ready)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_000L))

        assertEquals(0, provider.pauseCount)
        assertEquals(listOf("load"), listener.events)
        assertSame(provider.readyView, shownChild(view))
    }

    @Test
    fun `detach cancels the pending timeout`() {
        val view = attachedView(timeout = Milliseconds(1_000L))
        view.setListener(listener)
        shadowOf(Looper.getMainLooper()).idle()

        (view.parent as LinearLayout).removeView(view)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_000L))

        // Only the detach pause, not a timeout pause on top of it — and no outcome delivered.
        assertEquals(1, provider.pauseCount)
        assertTrue(listener.events.isEmpty())
    }

    @Test
    fun `claims the gesture from a horizontal host and releases it on a vertical move`() {
        val view = attachedView()
        provider.readyView = View(activity)
        provider.report(EmbeddedBlockState.Ready)

        var disallowed: Boolean? = null
        // LinearLayout propagates requestDisallowInterceptTouchEvent up; capture it via a spy parent.
        val spy = object : LinearLayout(activity) {
            override fun requestDisallowInterceptTouchEvent(disallow: Boolean) {
                disallowed = disallow
                super.requestDisallowInterceptTouchEvent(disallow)
            }
        }
        (view.parent as LinearLayout).removeView(view)
        spy.addView(view, 500, 300)
        activity.setContentView(spy)
        showWindow(view)

        fun motion(action: Int, x: Float, y: Float): MotionEvent =
            MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), action, x, y, 0)

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 100f, 10f))
        assertEquals(true, disallowed)

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, 105f, 300f))
        assertEquals(false, disallowed)

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, 105f, 300f))
        assertEquals(false, disallowed)
    }

    @Test
    fun `a loading block does not claim gestures`() {
        // The placeholder is not interactive: the host must keep scrolling/paging freely.
        val view = attachedView()

        var disallowed: Boolean? = null
        val spy = object : LinearLayout(activity) {
            override fun requestDisallowInterceptTouchEvent(disallow: Boolean) {
                disallowed = disallow
                super.requestDisallowInterceptTouchEvent(disallow)
            }
        }
        (view.parent as LinearLayout).removeView(view)
        spy.addView(view, 500, 300)
        activity.setContentView(spy)
        showWindow(view)

        val now = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 100f, 10f, 0))

        assertNull(disallowed)
    }

    @OptIn(InternalMindboxApi::class)
    @Test
    fun `release frees the content through the controller`() {
        val view = attachedView()
        (view.parent as LinearLayout).removeView(view)
        shadowOf(Looper.getMainLooper()).idle()

        // The Compose wrapper calls this when the composable leaves the composition for good.
        view.release()

        assertEquals(1, provider.releaseCount)
    }

    @OptIn(InternalMindboxApi::class)
    @Test
    fun `a released block stops holding on to the host lifecycle`() {
        val host = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        host.registry.currentState = Lifecycle.State.RESUMED
        val view = blockView()
        val root = LinearLayout(activity).apply { addView(view, 500, 300) }
        root.setViewTreeLifecycleOwner(host)
        activity.setContentView(root)
        showWindow(view)
        assertEquals(1, host.registry.observerCount)

        view.release()

        // A Compose host releases a block every time it leaves the composition, and the observer
        // holds the view: one that stays subscribed is kept alive until the whole screen dies.
        assertEquals(0, host.registry.observerCount)
    }

    @OptIn(InternalMindboxApi::class)
    @Test
    fun `a callback queued before the release never reaches the host`() {
        val view = attachedView()
        view.setListener(listener)
        shadowOf(Looper.getMainLooper()).idle()
        listener.events.clear()

        // The outcome is queued for the next main-loop pass; the host lets the block go before
        // that pass runs.
        provider.report(EmbeddedBlockState.Empty)
        view.release()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(listener.events.isEmpty())
    }

    @Test
    fun `a blank place name asks for content for nobody`() {
        mockkObject(MindboxEventManager)
        try {
            every { MindboxEventManager.embeddedPlaceRequested(any()) } just Runs

            // Built the way a host would with an unset XML attribute: a name made of spaces is
            // no name, and it must not reach the in-app pipeline as a trigger either.
            val view = MindboxEmbeddedBlockView(activity, "   ")
            attach(view)

            assertNull(view.placeSystemName)
            verify(exactly = 0) { MindboxEventManager.embeddedPlaceRequested(any()) }
        } finally {
            unmockkObject(MindboxEventManager)
        }
    }

    @Test
    fun `the destroyed host screen frees the content`() {
        val host = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        host.registry.currentState = Lifecycle.State.RESUMED
        val view = blockView()
        val root = LinearLayout(activity).apply { addView(view, 500, 300) }
        root.setViewTreeLifecycleOwner(host)
        activity.setContentView(root)
        showWindow(view)

        host.registry.currentState = Lifecycle.State.DESTROYED
        shadowOf(Looper.getMainLooper()).idle()

        // A host that never calls release() (a plain Activity or Fragment) must not leak a
        // WebView per block; the view tree's lifecycle owner closes it.
        assertEquals(1, provider.releaseCount)
    }
}
