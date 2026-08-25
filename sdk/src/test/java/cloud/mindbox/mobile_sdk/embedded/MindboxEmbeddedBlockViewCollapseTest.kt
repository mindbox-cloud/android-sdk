package cloud.mindbox.mobile_sdk.embedded

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.models.InAppStub
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.Closeable

/**
 * The collapse rule: space once ceded to the host is not taken back for a placeholder — the block
 * re-resolves on every return to the screen, and only shown content expands it again.
 */
@RunWith(RobolectricTestRunner::class)
class MindboxEmbeddedBlockViewCollapseTest {

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

        override fun start() {
            onStateChange?.invoke(EmbeddedBlockState.Ready)
        }

        override fun pause() = Unit

        override fun release() = Unit
    }

    private class FailingProvider(context: Activity) : EmbeddedContentProvider {
        override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
        override val contentView: View = View(context)

        override fun start() {
            onStateChange?.invoke(EmbeddedBlockState.Failed)
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

    private fun attach(view: MindboxEmbeddedBlockView) {
        activity.setContentView(LinearLayout(activity).apply { addView(view, 500, 300) })
        idle()
        dispatchWindowVisibility(view, View.VISIBLE)
        idle()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun leaveAndReturn(view: MindboxEmbeddedBlockView) {
        dispatchWindowVisibility(view, View.GONE)
        idle()
        dispatchWindowVisibility(view, View.VISIBLE)
        idle()
    }

    @Test
    fun `first appearance reserves the space with the placeholder`() {
        val view = buildView()

        attach(view)

        assertEquals(View.VISIBLE, view.visibility)
    }

    @Test
    fun `re-entering the screen with an empty place does not flash the placeholder`() {
        val view = buildView()
        attach(view)
        blocksRegistry.lastHandle?.onContentResolved(null)
        idle()
        assertEquals(View.GONE, view.visibility)

        leaveAndReturn(view)

        // The block is loading again, but the space it gave back is not reclaimed for a retry.
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `content expands a collapsed block again`() {
        val view = buildView()
        attach(view)
        blocksRegistry.lastHandle?.onContentResolved(null)
        idle()
        leaveAndReturn(view)

        blocksRegistry.lastHandle?.onContentResolved(embeddedContent())
        idle()

        assertEquals(View.VISIBLE, view.visibility)
    }

    @Test
    fun `a collapse after shown content still collapses and revives the same way`() {
        val view = buildView()
        attach(view)
        blocksRegistry.lastHandle?.onContentResolved(embeddedContent())
        idle()
        assertEquals(View.VISIBLE, view.visibility)

        blocksRegistry.lastHandle?.onContentResolved(null)
        idle()
        assertEquals(View.GONE, view.visibility)

        leaveAndReturn(view)
        assertEquals(View.GONE, view.visibility)

        blocksRegistry.lastHandle?.onContentResolved(embeddedContent())
        idle()
        assertEquals(View.VISIBLE, view.visibility)
    }

    @Test
    fun `an error view given after the collapse does not reopen the space when the retry fails too`() {
        val view = buildView(provider = { FailingProvider(activity) })
        attach(view)
        blocksRegistry.lastHandle?.onContentResolved(embeddedContent())
        idle()
        assertEquals(View.GONE, view.visibility)

        view.setErrorView(View(activity))
        leaveAndReturn(view)
        blocksRegistry.lastHandle?.onContentResolved(embeddedContent())
        idle()

        // The retry failed the same way the block already settled on: the error view is remembered
        // for a load that starts the cycle anew, not for a pass across the screen.
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `an error view given after the collapse shows once content has started the cycle anew`() {
        val providers = ArrayDeque(listOf(FailingProvider(activity), ReadyProvider(activity)))
        val view = buildView(provider = { providers.removeFirst() })
        attach(view)
        blocksRegistry.lastHandle?.onContentResolved(embeddedContent())
        idle()
        assertEquals(View.GONE, view.visibility)
        view.setErrorView(View(activity))

        // Content that actually appeared ends the settlement — the block is an ordinary one again...
        leaveAndReturn(view)
        blocksRegistry.lastHandle?.onContentResolved(embeddedContent())
        idle()
        assertEquals(View.VISIBLE, view.visibility)

        // ...so the next failure is entitled to the error view the host gave meanwhile.
        lastProvider?.onStateChange?.invoke(EmbeddedBlockState.Failed)
        idle()
        assertEquals(View.VISIBLE, view.visibility)
    }

    private fun embeddedContent(): InAppType.Embedded = InAppStub.getEmbedded()
}
