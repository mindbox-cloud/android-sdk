package cloud.mindbox.mobile_sdk.embedded

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.PlaceShowReservation
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.PlaceKey
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
        override fun onBlockContentDropped(placeSystemName: PlaceKey) {}

        var lastHandle: EmbeddedBlockHandle? = null

        override fun register(placeSystemName: PlaceKey, handle: EmbeddedBlockHandle): Closeable {
            lastHandle = handle
            return Closeable { lastHandle = null }
        }

        override fun onBlockAppeared(placeSystemName: PlaceKey) = Unit

        override fun startListening() = Unit

        override fun isLiveSession(sessionEpoch: Long): Boolean = true

        override fun deferUntilReturnChecked(): Boolean = false

        override fun onAppResumedOn(activity: Activity) = Unit

        override fun reserveShow(placeSystemName: PlaceKey, content: InAppType.Embedded, answer: EmbeddedPlaceAnswer) =
            PlaceShowReservation.RESERVED

        override fun onReturnCheckOver() = Unit
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
            onStateChange?.invoke(EmbeddedBlockState.Failed(MindboxEmbeddedBlockFailReason.NETWORK_ERROR))
        }

        override fun pause() = Unit

        override fun release() = Unit
    }

    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val blocksRegistry = FakeBlocksRegistry()
    private var lastProvider: EmbeddedContentProvider? = null
    private var isAppInForeground = true

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
                isAppInForeground = { isAppInForeground },
                hostActivity = { activity },
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
    fun `shown content the place no longer has stays on screen until the block leaves, then the block returns collapsed and revives the same way`() {
        val view = buildView()
        attach(view)
        blocksRegistry.lastHandle?.onContentResolved(embeddedContent())
        idle()
        assertEquals(View.VISIBLE, view.visibility)

        blocksRegistry.lastHandle?.onContentResolved(null)
        idle()
        assertEquals(View.VISIBLE, view.visibility)

        dispatchWindowVisibility(view, View.GONE)
        idle()
        assertEquals(View.GONE, view.visibility)

        dispatchWindowVisibility(view, View.VISIBLE)
        idle()
        assertEquals(View.GONE, view.visibility)

        blocksRegistry.lastHandle?.onContentResolved(embeddedContent())
        idle()
        assertEquals(View.VISIBLE, view.visibility)
    }

    @Test
    fun `shown content the place no longer has survives the app going to background and collapses once the block leaves the screen inside the app`() {
        val view = buildView()
        attach(view)
        blocksRegistry.lastHandle?.onContentResolved(embeddedContent())
        idle()
        blocksRegistry.lastHandle?.onContentResolved(null)
        idle()

        isAppInForeground = false
        dispatchWindowVisibility(view, View.GONE)
        idle()
        assertEquals(View.VISIBLE, view.visibility)

        isAppInForeground = true
        dispatchWindowVisibility(view, View.VISIBLE)
        idle()
        assertEquals(View.VISIBLE, view.visibility)

        dispatchWindowVisibility(view, View.GONE)
        idle()
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `shown content the place no longer has collapses before its first frame when the app comes back from background on another screen`() {
        val view = buildView()
        attach(view)
        blocksRegistry.lastHandle?.onContentResolved(embeddedContent())
        idle()
        blocksRegistry.lastHandle?.onContentResolved(null)
        idle()
        isAppInForeground = false
        dispatchWindowVisibility(view, View.GONE)
        idle()
        assertEquals(View.VISIBLE, view.visibility)

        blocksRegistry.lastHandle?.onAppResumedOn(Robolectric.buildActivity(Activity::class.java).get())
        idle()

        assertEquals(View.GONE, view.visibility)

        isAppInForeground = true
        dispatchWindowVisibility(view, View.VISIBLE)
        idle()
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `an operation that empties the place collapses the shown block at once`() {
        val view = buildView()
        attach(view)
        blocksRegistry.lastHandle?.onContentResolved(embeddedContent())
        idle()

        blocksRegistry.lastHandle?.onContentResolved(null, placeAnswer(isByOperation = true))
        idle()

        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `nothing to show that reached the block while it was away collapses it before it shows again`() {
        val view = buildView()
        attach(view)
        blocksRegistry.lastHandle?.onContentResolved(embeddedContent())
        idle()
        dispatchWindowVisibility(view, View.GONE)
        idle()
        blocksRegistry.lastHandle?.onContentResolved(null)

        dispatchWindowVisibility(view, View.VISIBLE)

        assertEquals(View.GONE, view.visibility)
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

        leaveAndReturn(view)
        blocksRegistry.lastHandle?.onContentResolved(embeddedContent())
        idle()
        assertEquals(View.VISIBLE, view.visibility)

        lastProvider?.onStateChange?.invoke(EmbeddedBlockState.Failed(MindboxEmbeddedBlockFailReason.NETWORK_ERROR))
        idle()
        assertEquals(View.VISIBLE, view.visibility)
    }

    private fun embeddedContent(): InAppType.Embedded = InAppStub.getEmbedded()
}
