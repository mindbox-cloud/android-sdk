package cloud.mindbox.mobile_sdk.embedded

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.models.InAppStub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.Closeable

/**
 * What a wrapper sees and says: the appearance the block reports, and the host visibility it is
 * told about. A wrapper lays the block out itself, so it needs the decision the view would have
 * applied to its own `visibility` — and it has to be able to say that its screen went away when the
 * window cannot say it.
 */
@RunWith(RobolectricTestRunner::class)
class MindboxEmbeddedBlockViewWrapperHooksTest {

    private class FakeBlocksRegistry : EmbeddedBlocksRegistry {
        var lastHandle: EmbeddedBlockHandle? = null

        override fun register(placeSystemName: String, handle: EmbeddedBlockHandle): Closeable {
            lastHandle = handle
            return Closeable { lastHandle = null }
        }

        override fun onBlockAppeared(placeSystemName: String) = Unit

        override fun startListening() = Unit
    }

    private class ReadyProvider(context: Activity) : EmbeddedContentProvider {
        override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
        override val contentView: View = View(context)

        var startCount = 0
        var pauseCount = 0

        override fun start() {
            startCount++
            onStateChange?.invoke(EmbeddedBlockState.Ready)
        }

        override fun pause() {
            pauseCount++
        }

        override fun release() = Unit
    }

    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val blocksRegistry = FakeBlocksRegistry()
    private lateinit var provider: ReadyProvider

    private fun buildView(): MindboxEmbeddedBlockView =
        MindboxEmbeddedBlockView(
            activity,
            null,
            "main-screen-top",
            contentController = EmbeddedBlockContentController(
                placeSystemName = "main-screen-top",
                providerFactory = { _, _ -> ReadyProvider(activity).also { provider = it } },
                blocksRegistry = { blocksRegistry },
            ),
        )

    private fun attach(view: MindboxEmbeddedBlockView) {
        activity.setContentView(LinearLayout(activity).apply { addView(view, 500, 300) })
        idle()
        dispatchWindowVisibility(view, View.VISIBLE)
        idle()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Resolves the place to content, which is what builds the provider. */
    private fun showContent() {
        blocksRegistry.lastHandle?.onContentResolved(InAppStub.getEmbedded() as InAppType.Embedded)
        idle()
    }

    /** Fails the page behind a place that already resolved to content. */
    private fun failContent() {
        provider.onStateChange?.invoke(EmbeddedBlockState.Failed)
        idle()
    }

    @Test
    fun `subscribing hands out the appearance the block already has`() {
        val view = buildView()
        attach(view)
        blocksRegistry.lastHandle?.onContentResolved(null)
        idle()

        // The place settled before anybody subscribed, and its only report would otherwise be lost:
        // a wrapper built after the outcome would sit on a loading screen forever.
        val seen = mutableListOf<MindboxEmbeddedBlockAppearance>()
        view.setAppearanceObserver { seen.add(it) }

        assertEquals(listOf(MindboxEmbeddedBlockAppearance.COLLAPSED), seen)
    }

    @Test
    fun `the appearance follows the block from loading to content`() {
        val view = buildView()
        val seen = mutableListOf<MindboxEmbeddedBlockAppearance>()
        view.setAppearanceObserver { seen.add(it) }

        attach(view)
        assertEquals(MindboxEmbeddedBlockAppearance.PLACEHOLDER, seen.last())

        showContent()

        assertEquals(MindboxEmbeddedBlockAppearance.CONTENT, seen.last())
    }

    @Test
    fun `an empty place collapses even when the host set an error view`() {
        val view = buildView()
        view.setErrorView(View(activity))
        val seen = mutableListOf<MindboxEmbeddedBlockAppearance>()
        view.setAppearanceObserver { seen.add(it) }
        attach(view)

        blocksRegistry.lastHandle?.onContentResolved(null)
        idle()

        // An error view opts into showing a failure, not into filling a place that was never meant
        // to hold anything.
        assertEquals(MindboxEmbeddedBlockAppearance.COLLAPSED, seen.last())
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `a host that hides the block pauses its content and shows it again on return`() {
        val view = buildView()
        attach(view)
        showContent()
        val startsWhenShown = provider.startCount

        view.setHostVisible(false)
        idle()
        assertEquals(1, provider.pauseCount)

        view.setHostVisible(true)
        idle()

        // The window never changed — only the wrapper's word did, and it drives the same switch.
        assertTrue(provider.startCount > startsWhenShown)
    }

    @Test
    fun `the same host visibility twice changes nothing`() {
        val view = buildView()
        attach(view)
        showContent()

        view.setHostVisible(false)
        view.setHostVisible(false)
        idle()

        assertEquals(1, provider.pauseCount)
    }

    @Test
    fun `taking the error view away collapses the failure it was showing`() {
        val view = buildView()
        view.setErrorView(View(activity))
        val seen = mutableListOf<MindboxEmbeddedBlockAppearance>()
        view.setAppearanceObserver { seen.add(it) }
        attach(view)
        showContent()
        failContent()
        assertEquals(MindboxEmbeddedBlockAppearance.ERROR, seen.last())

        view.setErrorView(null)

        // Whether a failure is shown at all is this view's doing, so taking it away is not a swap of
        // screens — it is the block going back to the collapse it would have had, and saying so.
        assertEquals(MindboxEmbeddedBlockAppearance.COLLAPSED, seen.last())
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `one error view swapped for another keeps the failure shown`() {
        val view = buildView()
        view.setErrorView(View(activity))
        val seen = mutableListOf<MindboxEmbeddedBlockAppearance>()
        view.setAppearanceObserver { seen.add(it) }
        attach(view)
        showContent()
        failContent()

        val next = View(activity)
        view.setErrorView(next)

        assertEquals(MindboxEmbeddedBlockAppearance.ERROR, seen.last())
        assertEquals(View.VISIBLE, view.visibility)
    }

    @Test
    fun `an error view given to an already collapsed block does not expand it`() {
        val view = buildView()
        val seen = mutableListOf<MindboxEmbeddedBlockAppearance>()
        view.setAppearanceObserver { seen.add(it) }
        attach(view)
        showContent()
        failContent()
        assertEquals(MindboxEmbeddedBlockAppearance.COLLAPSED, seen.last())

        view.setErrorView(View(activity))

        // Reopening space the layout has already reclaimed would make it jump: the view waits for
        // the next load.
        assertEquals(MindboxEmbeddedBlockAppearance.COLLAPSED, seen.last())
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `taking the error view away collapses a failure still shown after a return`() {
        val view = buildView()
        view.setErrorView(View(activity))
        val seen = mutableListOf<MindboxEmbeddedBlockAppearance>()
        view.setAppearanceObserver { seen.add(it) }
        attach(view)
        showContent()
        failContent()
        assertEquals(MindboxEmbeddedBlockAppearance.ERROR, seen.last())

        // Off the screen and back: the failed block drops its page and waits for a new answer, so
        // the state is `Loading` again while the error view is still the thing on screen.
        dispatchWindowVisibility(view, View.GONE)
        idle()
        dispatchWindowVisibility(view, View.VISIBLE)
        idle()
        assertEquals(MindboxEmbeddedBlockAppearance.ERROR, seen.last())

        view.setErrorView(null)

        assertEquals(MindboxEmbeddedBlockAppearance.COLLAPSED, seen.last())
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `an error view swapped after a return replaces the one still on screen`() {
        val view = buildView()
        view.setErrorView(View(activity))
        val seen = mutableListOf<MindboxEmbeddedBlockAppearance>()
        view.setAppearanceObserver { seen.add(it) }
        attach(view)
        showContent()
        failContent()

        dispatchWindowVisibility(view, View.GONE)
        idle()
        dispatchWindowVisibility(view, View.VISIBLE)
        idle()

        val next = View(activity)
        view.setErrorView(next)

        assertEquals(MindboxEmbeddedBlockAppearance.ERROR, seen.last())
        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(view, next.parent)
    }

    @Test
    fun `a released block stops its content before letting it go`() {
        val view = buildView()
        attach(view)
        showContent()

        view.release()
        idle()

        // Released while the window still says it is shown: the switch has to hear it here, or it
        // goes on believing the content runs and pauses a controller that is already released.
        assertEquals(1, provider.pauseCount)
    }

    @Test
    fun `a wrapper callback that throws on subscribing does not reach the host`() {
        val view = buildView()
        attach(view)

        view.setAppearanceObserver { throw IllegalStateException("the channel is not ready yet") }

        // The same callback is guarded everywhere else; the immediate call on subscribing cannot be
        // the one place where a wrapper's exception takes the host down with it.
        val seen = mutableListOf<MindboxEmbeddedBlockAppearance>()
        view.setAppearanceObserver { seen.add(it) }
        assertEquals(1, seen.size)
    }

    @Test
    fun `a released block stays stopped even when the window says it is shown`() {
        val view = buildView()
        attach(view)
        showContent()
        view.release()
        idle()
        val startsBefore = provider.startCount

        dispatchWindowVisibility(view, View.VISIBLE)
        idle()

        assertEquals(startsBefore, provider.startCount)
    }
}
