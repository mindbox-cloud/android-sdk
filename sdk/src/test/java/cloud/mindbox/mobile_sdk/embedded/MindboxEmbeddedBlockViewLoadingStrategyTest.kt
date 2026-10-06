package cloud.mindbox.mobile_sdk.embedded

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.managers.SharedPreferencesManager
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.PlaceKey
import cloud.mindbox.mobile_sdk.models.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * The loading strategy: what the block shows before the SDK answers, how the place memory is
 * written by the outcomes, and when the reveal animation runs.
 */
@RunWith(RobolectricTestRunner::class)
class MindboxEmbeddedBlockViewLoadingStrategyTest {

    private class ScriptedProvider(context: Activity) : EmbeddedContentProvider {
        override var onStateChange: ((EmbeddedBlockState) -> Unit)? = null
        override val contentView: View = View(context)

        override fun start() = Unit

        override fun pause() = Unit

        override fun release() = Unit
    }

    private open class RecordingAnimation(
        private val enabled: Boolean = true,
    ) : EmbeddedBlockRevealAnimation(areSystemAnimationsEnabled = { enabled }) {
        val calls = mutableListOf<String>()

        override fun crossfade(incoming: View, outgoing: View?, onEnd: () -> Unit): Animator {
            calls.add("fade")
            onEnd()
            return ValueAnimator.ofFloat(0f, 1f)
        }

        override fun growHeight(view: View, targetHeight: Int, onEnd: () -> Unit): Animator {
            calls.add("grow:$targetHeight")
            view.layoutParams?.height = targetHeight
            onEnd()
            return ValueAnimator.ofInt(0, targetHeight)
        }
    }

    /** Growth that stays mid-flight until the test finishes it — the clip window is observable. */
    private class PendingGrowthAnimation : RecordingAnimation() {
        private var pendingEnd: (() -> Unit)? = null
        private var pendingView: View? = null
        private var pendingTarget: Int = 0

        override fun growHeight(view: View, targetHeight: Int, onEnd: () -> Unit): Animator {
            calls.add("grow:$targetHeight")
            pendingEnd = onEnd
            pendingView = view
            pendingTarget = targetHeight
            return ValueAnimator.ofInt(0, targetHeight)
        }

        fun finishGrowth() {
            pendingView?.layoutParams?.height = pendingTarget
            pendingEnd?.invoke()
            pendingEnd = null
        }
    }

    /** The real memory over an in-memory store: the view is tested against the real write rules. */
    private class TestPlaceMemory(known: Boolean = false) {
        private var stored: String = ""
        val writes = mutableListOf<String>()
        val memory = EmbeddedBlockPlaceMemory(
            readRecordsJson = { stored },
            writeRecordsJson = { json ->
                stored = json
                writes.add(json)
            },
            now = { Timestamp(0L) },
        )

        init {
            if (known) memory.rememberShownContent(PlaceKey.of("main-screen-top"))
            writes.clear()
        }

        fun knows(place: String): Boolean = memory.hasShownContent(PlaceKey.of(place))
    }

    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val blocksRegistry = RecordingBlocksRegistry()
    private var provider: ScriptedProvider? = null

    private fun buildView(
        loadingStrategy: MindboxEmbeddedBlockLoadingStrategy? = null,
        animatesReveal: Boolean? = null,
        placeMemory: TestPlaceMemory = TestPlaceMemory(),
        animation: EmbeddedBlockRevealAnimation = RecordingAnimation(),
        providerFactory: (InAppType.Embedded, Milliseconds) -> EmbeddedContentProvider = { _, _ ->
            ScriptedProvider(activity).also { provider = it }
        },
    ): MindboxEmbeddedBlockView =
        MindboxEmbeddedBlockView(
            activity,
            null,
            "main-screen-top",
            loadingStrategy = loadingStrategy,
            animatesReveal = animatesReveal,
            contentController = EmbeddedBlockContentController(
                placeSystemName = "main-screen-top",
                providerFactory = providerFactory,
                blocksRegistry = { blocksRegistry },
            ),
            placeMemory = placeMemory.memory,
            revealAnimation = animation,
        )

    private fun contentForThePlace() {
        blocksRegistry.lastHandle?.onContentResolved(InAppStub.getEmbedded())
        idleMainLooper()
    }

    private fun pageReports(state: EmbeddedBlockState) {
        provider?.onStateChange?.invoke(state)
        idleMainLooper()
    }

    // --- The first look, decided at creation ---

    @Test
    fun `the first look is the strategy plus the place memory and nothing else`() {
        fun initial(
            strategy: MindboxEmbeddedBlockLoadingStrategy,
            hasShown: Boolean,
        ): MindboxEmbeddedBlockAppearance =
            MindboxEmbeddedBlockView.initialAppearanceFor(strategy) { hasShown }

        assertEquals(
            MindboxEmbeddedBlockAppearance.PLACEHOLDER,
            initial(MindboxEmbeddedBlockLoadingStrategy.PLACEHOLDER, hasShown = false),
        )
        assertEquals(
            MindboxEmbeddedBlockAppearance.PLACEHOLDER,
            initial(MindboxEmbeddedBlockLoadingStrategy.PLACEHOLDER, hasShown = true),
        )
        assertEquals(
            MindboxEmbeddedBlockAppearance.COLLAPSED,
            initial(MindboxEmbeddedBlockLoadingStrategy.HIDDEN, hasShown = false),
        )
        assertEquals(
            MindboxEmbeddedBlockAppearance.COLLAPSED,
            initial(MindboxEmbeddedBlockLoadingStrategy.HIDDEN, hasShown = true),
        )
        assertEquals(
            MindboxEmbeddedBlockAppearance.COLLAPSED,
            initial(MindboxEmbeddedBlockLoadingStrategy.AUTOMATIC, hasShown = false),
        )
        assertEquals(
            MindboxEmbeddedBlockAppearance.PLACEHOLDER,
            initial(MindboxEmbeddedBlockLoadingStrategy.AUTOMATIC, hasShown = true),
        )
    }

    @Test
    fun `a hidden block is born collapsed with no placeholder inside`() {
        val view = buildView(loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.HIDDEN)

        assertEquals(View.GONE, view.visibility)
        assertEquals(0, view.childCount)
    }

    @Test
    fun `a placeholder block is born visible with the placeholder inside`() {
        val view = buildView(loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.PLACEHOLDER)

        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(1, view.childCount)
    }

    @Test
    fun `an automatic block starts hidden on a device where the place never showed content`() {
        val view = buildView()

        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `an automatic block starts with a placeholder once the place has shown content`() {
        val view = buildView(placeMemory = TestPlaceMemory(known = true))

        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(1, view.childCount)
    }

    @Test
    fun `the defaults are automatic with the reveal animated`() {
        val view = buildView()

        assertEquals(MindboxEmbeddedBlockLoadingStrategy.AUTOMATIC, view.loadingStrategy)
        assertTrue(view.animatesReveal)
    }

    // --- The hidden block through the outcomes ---

    @Test
    fun `a hidden block stays collapsed while loading and shows only confirmed content`() {
        val view = buildView(loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.HIDDEN)
        activity.attachBlock(view)
        assertEquals(View.GONE, view.visibility)

        contentForThePlace()
        assertEquals(View.GONE, view.visibility)

        pageReports(EmbeddedBlockState.Ready)
        assertEquals(View.VISIBLE, view.visibility)
    }

    @Test
    fun `a hidden block never takes the space for an error screen`() {
        val view = buildView(loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.HIDDEN)
        view.setErrorView(View(activity))
        activity.attachBlock(view)

        blocksRegistry.lastHandle?.onConfigUnavailable()
        idleMainLooper()

        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `a hidden block still reports its outcomes`() {
        val listener = RecordingListener()
        val view = buildView(loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.HIDDEN)
        view.setListener(listener)
        activity.attachBlock(view)

        blocksRegistry.lastHandle?.onContentResolved(null)
        idleMainLooper()

        assertEquals(listOf("empty"), listener.events)
        assertEquals(View.GONE, view.visibility)
    }

    // --- The place memory written by the outcomes ---

    @Test
    fun `shown content writes the place down`() {
        val memory = TestPlaceMemory()
        val view = buildView(placeMemory = memory)
        activity.attachBlock(view)

        contentForThePlace()
        pageReports(EmbeddedBlockState.Ready)

        assertTrue(memory.knows("main-screen-top"))
    }

    @Test
    fun `an empty answer strikes the place out`() {
        val memory = TestPlaceMemory(known = true)
        val view = buildView(placeMemory = memory)
        activity.attachBlock(view)

        blocksRegistry.lastHandle?.onContentResolved(null)
        idleMainLooper()

        assertFalse(memory.knows("main-screen-top"))
    }

    @Test
    fun `a failure says nothing about the place and leaves the memory alone`() {
        val memory = TestPlaceMemory(known = true)
        val view = buildView(placeMemory = memory)
        activity.attachBlock(view)

        blocksRegistry.lastHandle?.onConfigUnavailable()
        idleMainLooper()

        assertEquals(emptyList<String>(), memory.writes)
        assertTrue(memory.knows("main-screen-top"))
    }

    // --- The reveal animation and its gates ---

    @Test
    fun `content arriving into a hidden block fades in and grows the height`() {
        val animation = RecordingAnimation()
        val view = buildView(
            loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.HIDDEN,
            animation = animation,
        )
        activity.attachBlock(view)

        contentForThePlace()
        pageReports(EmbeddedBlockState.Ready)

        assertEquals(listOf("fade", "grow:300"), animation.calls)
    }

    @Test
    fun `content arriving into a placeholder block fades in without growing`() {
        val animation = RecordingAnimation()
        val view = buildView(
            loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.PLACEHOLDER,
            animation = animation,
        )
        activity.attachBlock(view)

        contentForThePlace()
        pageReports(EmbeddedBlockState.Ready)

        assertEquals(listOf("fade"), animation.calls)
    }

    @Test
    fun `animatesReveal off swaps the looks at once`() {
        val animation = RecordingAnimation()
        val view = buildView(
            loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.HIDDEN,
            animatesReveal = false,
            animation = animation,
        )
        activity.attachBlock(view)

        contentForThePlace()
        pageReports(EmbeddedBlockState.Ready)

        assertEquals(emptyList<String>(), animation.calls)
        assertEquals(View.VISIBLE, view.visibility)
    }

    @Test
    fun `system animations off disable the reveal the way reduce motion does`() {
        val animation = RecordingAnimation(enabled = false)
        val view = buildView(
            loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.HIDDEN,
            animation = animation,
        )
        activity.attachBlock(view)

        contentForThePlace()
        pageReports(EmbeddedBlockState.Ready)

        assertEquals(emptyList<String>(), animation.calls)
        assertEquals(View.VISIBLE, view.visibility)
    }

    @Test
    fun `a wrapper that owns the layout grows the height itself - the view only fades`() {
        val animation = RecordingAnimation()
        val view = buildView(
            loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.HIDDEN,
            animation = animation,
        )
        view.setAppearanceObserver { }
        activity.attachBlock(view)

        contentForThePlace()
        pageReports(EmbeddedBlockState.Ready)

        assertEquals(listOf("fade"), animation.calls)
    }

    @Test
    fun `content shown again on a return is not a reveal`() {
        val animation = RecordingAnimation()
        val view = buildView(
            loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.HIDDEN,
            animation = animation,
        )
        activity.attachBlock(view)
        contentForThePlace()
        pageReports(EmbeddedBlockState.Ready)
        animation.calls.clear()

        leaveAndReturn(view)

        assertEquals(emptyList<String>(), animation.calls)
        assertEquals(View.VISIBLE, view.visibility)
    }

    @Test
    fun `a collapse lands at once - only the arrival of content animates`() {
        val animation = RecordingAnimation()
        val view = buildView(animation = animation)
        activity.attachBlock(view)

        blocksRegistry.lastHandle?.onContentResolved(null)
        idleMainLooper()

        assertEquals(emptyList<String>(), animation.calls)
        assertEquals(View.GONE, view.visibility)
    }

    // --- The settled rules the strategies lean on ---

    @Test
    fun `a hidden block that went empty does not reopen for the placeholder of a retry`() {
        val view = buildView(loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.HIDDEN)
        activity.attachBlock(view)
        blocksRegistry.lastHandle?.onContentResolved(null)
        idleMainLooper()

        leaveAndReturn(view)

        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `the memory seam of the real view reads and writes the shared preferences`() {
        SharedPreferencesManager.with(activity)
        val stored = EmbeddedBlockPlaceMemory(now = { Timestamp(0L) })
        stored.rememberShownContent(PlaceKey.of("main-screen-top"))

        val appearance = MindboxEmbeddedBlockView.initialAppearance(
            activity,
            "main-screen-top",
            MindboxEmbeddedBlockLoadingStrategy.AUTOMATIC,
        )

        assertEquals(MindboxEmbeddedBlockAppearance.PLACEHOLDER, appearance)

        stored.forgetPlace(PlaceKey.of("main-screen-top"))
        assertFalse(stored.hasShownContent(PlaceKey.of("main-screen-top")))
    }

    @Test
    fun `the observer hears whether the change is an animated reveal`() {
        val heard = mutableListOf<Pair<MindboxEmbeddedBlockAppearance, Boolean>>()
        val view = buildView(loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.HIDDEN)
        view.setAppearanceObserver { shown -> heard.add(shown to view.isRevealAnimated) }
        activity.attachBlock(view)

        contentForThePlace()
        pageReports(EmbeddedBlockState.Ready)

        // The replay on subscribing and the Loading pass are never a reveal; the content is.
        assertEquals(false, heard.first().second)
        assertEquals(MindboxEmbeddedBlockAppearance.CONTENT to true, heard.last())
    }

    @Test
    fun `a wrapper subscribing after the content sees no reveal to animate`() {
        val view = buildView(loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.HIDDEN)
        activity.attachBlock(view)
        contentForThePlace()
        pageReports(EmbeddedBlockState.Ready)

        val heard = mutableListOf<Pair<MindboxEmbeddedBlockAppearance, Boolean>>()
        view.setAppearanceObserver { shown -> heard.add(shown to view.isRevealAnimated) }

        assertEquals(listOf(MindboxEmbeddedBlockAppearance.CONTENT to false), heard)
    }

    @Test
    fun `the growth reveals the content by clipping, not by resizing it every frame`() {
        val animation = PendingGrowthAnimation()
        val view = buildView(
            loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.HIDDEN,
            animation = animation,
        )
        activity.attachBlock(view)

        contentForThePlace()
        pageReports(EmbeddedBlockState.Ready)

        // The growth is running: the block's own height is animating up from zero…
        assertEquals(0, view.layoutParams.height)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 500, 0)

        // …while the content inside stays laid out at the full target height, revealed by the clip.
        val content = requireNotNull(provider).contentView
        assertEquals(300, content.measuredHeight)

        animation.finishGrowth()
        assertEquals(300, view.layoutParams.height)
    }

    @Test
    fun `a host height the block does not own is not grown - the content only fades`() {
        val animation = RecordingAnimation()
        val view = buildView(
            loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.HIDDEN,
            animation = animation,
        )
        activity.setContentView(
            android.widget.LinearLayout(activity).apply {
                addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            },
        )
        idleMainLooper()
        dispatchWindowVisibility(view, View.VISIBLE)
        idleMainLooper()

        contentForThePlace()
        pageReports(EmbeddedBlockState.Ready)

        assertEquals(listOf("fade"), animation.calls)
        assertEquals(View.VISIBLE, view.visibility)
    }

    @Test
    fun `a placeholder fades out with the reveal and is ready for the next loading`() {
        val view = buildView(
            loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.PLACEHOLDER,
            animation = EmbeddedBlockRevealAnimation(duration = Milliseconds(50L)),
        )
        val placeholder = View(activity)
        view.setPlaceholderView(placeholder)
        activity.setContentView(
            android.widget.LinearLayout(activity).apply {
                addView(view, 500, 300)
            },
        )
        idleMainLooper()
        dispatchWindowVisibility(view, View.VISIBLE)
        idleMainLooper()

        contentForThePlace()
        pageReports(EmbeddedBlockState.Ready)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(300L))

        // The shimmer faded out in step with the content and left ready for its next show.
        assertTrue(placeholder.parent == null)
        assertEquals(1f, placeholder.alpha)
        assertEquals(1f, requireNotNull(provider).contentView.alpha)

        // The next loading shows the same placeholder again — fully visible, not a faded ghost.
        pageReports(EmbeddedBlockState.Loading)
        assertTrue(placeholder.parent != null)
        assertEquals(1f, placeholder.alpha)
    }

    @Test
    fun `the xml attributes carry the strategy and the animation flag`() {
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(cloud.mindbox.mobile_sdk.R.attr.mindboxPlaceSystemName, "main-screen-top")
            .addAttribute(cloud.mindbox.mobile_sdk.R.attr.mindboxLoadingStrategy, "hidden")
            .addAttribute(cloud.mindbox.mobile_sdk.R.attr.mindboxAnimatesReveal, "false")
            .build()

        val view = MindboxEmbeddedBlockView(activity, attrs)

        assertEquals(MindboxEmbeddedBlockLoadingStrategy.HIDDEN, view.loadingStrategy)
        assertFalse(view.animatesReveal)
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `a hidden block that has shown content may keep a later failure with the error view`() {
        // Once content was shown the space is taken — a failure behaves like on any block, and
        // like on iOS: the birth-time settlement protects only the space never taken.
        val view = buildView(loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.HIDDEN)
        view.setErrorView(View(activity))
        activity.attachBlock(view)
        contentForThePlace()
        pageReports(EmbeddedBlockState.Ready)

        pageReports(EmbeddedBlockState.Failed(MindboxEmbeddedBlockFailReason.INTERNAL_ERROR))

        assertEquals(View.VISIBLE, view.visibility)
    }

    @Test
    fun `a block without a place starts hidden under the default strategy and answers empty`() {
        val listener = RecordingListener()
        val view = MindboxEmbeddedBlockView(activity, null, placeSystemName = null)
        view.setListener(listener)
        activity.attachBlock(view)

        assertEquals(View.GONE, view.visibility)
        assertEquals(listOf("empty"), listener.events)
    }
}
