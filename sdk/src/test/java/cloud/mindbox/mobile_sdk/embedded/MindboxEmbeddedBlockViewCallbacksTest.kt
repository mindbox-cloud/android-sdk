package cloud.mindbox.mobile_sdk.embedded

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
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
 * The three callbacks: which outcome reaches which one, the reason a failure carries, and the
 * one-callback-per-outcome rule with a reason that changes underneath it.
 */
@RunWith(RobolectricTestRunner::class)
class MindboxEmbeddedBlockViewCallbacksTest {

    private class FakeBlocksRegistry : EmbeddedBlocksRegistry {
        override fun onBlockContentDropped(placeSystemName: PlaceKey) {}

        var lastHandle: EmbeddedBlockHandle? = null

        override fun register(placeSystemName: PlaceKey, handle: EmbeddedBlockHandle): Closeable {
            lastHandle = handle
            return Closeable { lastHandle = null }
        }

        override fun onBlockAppeared(placeSystemName: PlaceKey) = Unit

        override fun startListening() = Unit
    }

    private class ScriptedProvider(context: Activity) : EmbeddedContentProvider {
        override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
        override val contentView: View = View(context)

        override fun start() = Unit

        override fun pause() = Unit

        override fun release() = Unit
    }

    private class RecordingListener : MindboxEmbeddedBlockListener {
        val events = mutableListOf<String>()

        override fun onLoad(view: MindboxEmbeddedBlockView) {
            events.add("load")
        }

        override fun onEmpty(view: MindboxEmbeddedBlockView) {
            events.add("empty")
        }

        override fun onFail(view: MindboxEmbeddedBlockView, reason: MindboxEmbeddedBlockFailReason) {
            events.add("fail:${reason.value}")
        }
    }

    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val blocksRegistry = FakeBlocksRegistry()
    private var provider: ScriptedProvider? = null
    private val listener = RecordingListener()

    private fun buildView(): MindboxEmbeddedBlockView =
        MindboxEmbeddedBlockView(
            activity,
            null,
            "main-screen-top",
            contentController = EmbeddedBlockContentController(
                placeSystemName = "main-screen-top",
                providerFactory = { _, _ -> ScriptedProvider(activity).also { provider = it } },
                blocksRegistry = { blocksRegistry },
            ),
        ).apply { setListener(listener) }

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

    private fun nothingForThePlace() {
        blocksRegistry.lastHandle?.onContentResolved(null)
        idle()
    }

    private fun contentForThePlace() {
        blocksRegistry.lastHandle?.onContentResolved(InAppStub.getEmbedded())
        idle()
    }

    private fun pageReports(state: EmbeddedBlockState) {
        provider?.onStateChange?.invoke(state)
        idle()
    }

    @Test
    fun `nothing for the place is heard as onEmpty and the block collapses`() {
        val view = buildView()
        attach(view)

        nothingForThePlace()

        assertEquals(listOf("empty"), listener.events)
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `an empty place collapses past an error view and is still onEmpty`() {
        val view = buildView()
        view.setErrorView(View(activity))
        attach(view)

        nothingForThePlace()

        assertEquals(listOf("empty"), listener.events)
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `shown content is heard as onLoad`() {
        val view = buildView()
        attach(view)

        contentForThePlace()
        pageReports(EmbeddedBlockState.Ready)

        assertEquals(listOf("load"), listener.events)
        assertEquals(View.VISIBLE, view.visibility)
    }

    @Test
    fun `a failed page is heard as onFail with the reason it failed for`() {
        val view = buildView()
        attach(view)

        contentForThePlace()
        pageReports(EmbeddedBlockState.Failed(MindboxEmbeddedBlockFailReason.NETWORK_ERROR))

        assertEquals(listOf("fail:networkError"), listener.events)
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `a failure that changes its reason is not heard twice`() {
        val view = buildView()
        attach(view)
        contentForThePlace()

        pageReports(EmbeddedBlockState.Failed(MindboxEmbeddedBlockFailReason.NETWORK_ERROR))
        pageReports(EmbeddedBlockState.Failed(MindboxEmbeddedBlockFailReason.INTERNAL_ERROR))

        assertEquals(listOf("fail:networkError"), listener.events)
    }

    @Test
    fun `an empty place that later fails is heard once per outcome`() {
        val view = buildView()
        attach(view)
        nothingForThePlace()

        leaveAndReturn(view)
        contentForThePlace()
        pageReports(EmbeddedBlockState.Failed(MindboxEmbeddedBlockFailReason.INTERNAL_ERROR))

        assertEquals(listOf("empty", "fail:internalError"), listener.events)
    }

    @Test
    fun `a listener registered after the failure hears the current reason once`() {
        val view = buildView()
        attach(view)
        contentForThePlace()
        pageReports(EmbeddedBlockState.Failed(MindboxEmbeddedBlockFailReason.INTERNAL_ERROR))

        val late = RecordingListener()
        view.setListener(late)
        idle()
        view.setListener(late)
        idle()

        assertEquals(listOf("fail:internalError"), late.events)
    }

    @Test
    fun `no answer from the SDK is heard as onFail with a network error and the block collapses`() {
        val view = buildView()
        attach(view)

        blocksRegistry.lastHandle?.onConfigUnavailable()
        idle()

        assertEquals(listOf("fail:networkError"), listener.events)
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `no answer from the SDK keeps the place when the host set an error view`() {
        val view = buildView()
        view.setErrorView(View(activity))
        attach(view)

        blocksRegistry.lastHandle?.onConfigUnavailable()
        idle()

        assertEquals(listOf("fail:networkError"), listener.events)
        assertEquals(View.VISIBLE, view.visibility)
    }

    @Test
    fun `the adapter lets a host override one callback and inherit the rest`() {
        val heard = mutableListOf<String>()
        val view = buildView().apply {
            setListener(
                object : MindboxEmbeddedBlockListenerAdapter() {
                    override fun onEmpty(view: MindboxEmbeddedBlockView) {
                        heard.add("empty")
                    }
                },
            )
        }
        attach(view)

        nothingForThePlace()

        assertEquals(listOf("empty"), heard)
    }

    @Test
    fun `a page that crashes on the way back is heard as onFail with an internal error`() {
        var starts = 0
        val view = MindboxEmbeddedBlockView(
            activity,
            null,
            "main-screen-top",
            contentController = EmbeddedBlockContentController(
                placeSystemName = "main-screen-top",
                providerFactory = { _, _ ->
                    object : EmbeddedContentProvider {
                        override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
                        override val contentView: View = View(activity)

                        override fun start() {
                            starts++
                            if (starts > 1) throw IllegalStateException("boom")
                            onStateChange?.invoke(EmbeddedBlockState.Ready)
                        }

                        override fun pause() = Unit

                        override fun release() = Unit
                    }
                },
                blocksRegistry = { blocksRegistry },
            ),
        ).apply { setListener(listener) }
        attach(view)
        contentForThePlace()
        assertEquals(listOf("load"), listener.events)

        leaveAndReturn(view)

        assertEquals(listOf("load", "fail:internalError"), listener.events)
        assertEquals(View.GONE, view.visibility)
    }
}
