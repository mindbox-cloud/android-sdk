package cloud.mindbox.mobile_sdk.embedded

import android.app.Activity
import android.view.View
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.Milliseconds
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * The three callbacks: which outcome reaches which one, the reason a failure carries, and the
 * one-callback-per-outcome rule with a reason that changes underneath it.
 */
@RunWith(RobolectricTestRunner::class)
class MindboxEmbeddedBlockViewCallbacksTest {

    private class ScriptedProvider(context: Activity) : EmbeddedContentProvider {
        override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
        override val contentView: View = View(context)

        override fun start() = Unit

        override fun pause() = Unit

        override fun release() = Unit
    }

    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val blocksRegistry = RecordingBlocksRegistry()
    private var provider: ScriptedProvider? = null
    private val listener = RecordingListener()

    private fun buildView(
        providerFactory: (InAppType.Embedded, Milliseconds) -> EmbeddedContentProvider = { _, _ ->
            ScriptedProvider(activity).also { provider = it }
        },
    ): MindboxEmbeddedBlockView =
        MindboxEmbeddedBlockView(
            activity,
            null,
            "main-screen-top",
            contentController = EmbeddedBlockContentController(
                placeSystemName = "main-screen-top",
                providerFactory = providerFactory,
                blocksRegistry = { blocksRegistry },
            ),
        ).apply { setListener(listener) }

    private fun nothingForThePlace() {
        blocksRegistry.lastHandle?.onContentResolved(null)
        idleMainLooper()
    }

    private fun contentForThePlace() {
        blocksRegistry.lastHandle?.onContentResolved(InAppStub.getEmbedded())
        idleMainLooper()
    }

    private fun pageReports(state: EmbeddedBlockState) {
        provider?.onStateChange?.invoke(state)
        idleMainLooper()
    }

    @Test
    fun `nothing for the place is heard as onEmpty and the block collapses`() {
        val view = buildView()
        activity.attachBlock(view)

        nothingForThePlace()

        assertEquals(listOf("empty"), listener.events)
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `an empty place collapses past an error view and is still onEmpty`() {
        val view = buildView()
        view.setErrorView(View(activity))
        activity.attachBlock(view)

        nothingForThePlace()

        assertEquals(listOf("empty"), listener.events)
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `shown content is heard as onLoad`() {
        val view = buildView()
        activity.attachBlock(view)

        contentForThePlace()
        pageReports(EmbeddedBlockState.Ready)

        assertEquals(listOf("load"), listener.events)
        assertEquals(View.VISIBLE, view.visibility)
    }

    @Test
    fun `a failed page is heard as onFail with the reason it failed for`() {
        val view = buildView()
        activity.attachBlock(view)

        contentForThePlace()
        pageReports(EmbeddedBlockState.Failed(MindboxEmbeddedBlockFailReason.NETWORK_ERROR))

        assertEquals(listOf("fail:networkError"), listener.events)
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `a failure that changes its reason is not heard twice`() {
        val view = buildView()
        activity.attachBlock(view)
        contentForThePlace()

        pageReports(EmbeddedBlockState.Failed(MindboxEmbeddedBlockFailReason.NETWORK_ERROR))
        pageReports(EmbeddedBlockState.Failed(MindboxEmbeddedBlockFailReason.INTERNAL_ERROR))

        assertEquals(listOf("fail:networkError"), listener.events)
    }

    @Test
    fun `an empty place that later fails is heard once per outcome`() {
        val view = buildView()
        activity.attachBlock(view)
        nothingForThePlace()

        leaveAndReturn(view)
        contentForThePlace()
        pageReports(EmbeddedBlockState.Failed(MindboxEmbeddedBlockFailReason.INTERNAL_ERROR))

        assertEquals(listOf("empty", "fail:internalError"), listener.events)
    }

    @Test
    fun `a listener registered after the failure hears the current reason once`() {
        val view = buildView()
        activity.attachBlock(view)
        contentForThePlace()
        pageReports(EmbeddedBlockState.Failed(MindboxEmbeddedBlockFailReason.INTERNAL_ERROR))

        val late = RecordingListener()
        view.setListener(late)
        idleMainLooper()
        view.setListener(late)
        idleMainLooper()

        assertEquals(listOf("fail:internalError"), late.events)
    }

    @Test
    fun `no answer from the SDK is heard as onFail with a network error and the block collapses`() {
        val view = buildView()
        activity.attachBlock(view)

        blocksRegistry.lastHandle?.onConfigUnavailable()
        idleMainLooper()

        assertEquals(listOf("fail:networkError"), listener.events)
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `no answer from the SDK keeps the place when the host set an error view`() {
        val view = buildView()
        view.setErrorView(View(activity))
        activity.attachBlock(view)

        blocksRegistry.lastHandle?.onConfigUnavailable()
        idleMainLooper()

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
        activity.attachBlock(view)

        nothingForThePlace()

        assertEquals(listOf("empty"), heard)
    }

    @Test
    fun `a page that crashes on the way back is heard as onFail with an internal error`() {
        var starts = 0
        val view = buildView { _, _ ->
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
        }
        activity.attachBlock(view)
        contentForThePlace()
        assertEquals(listOf("load"), listener.events)

        leaveAndReturn(view)

        assertEquals(listOf("load", "fail:internalError"), listener.events)
        assertEquals(View.GONE, view.visibility)
    }
}
