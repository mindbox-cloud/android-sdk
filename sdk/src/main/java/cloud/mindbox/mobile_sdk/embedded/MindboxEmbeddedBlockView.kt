package cloud.mindbox.mobile_sdk.embedded

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import cloud.mindbox.mobile_sdk.R
import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import kotlin.math.abs

/**
 * A drop-in container for an embedded Mindbox block.
 *
 * The host marks a *place* by [placeSystemName] and never learns what goes into it — the mobile
 * config decides through its `inlineBlocks` section and can change it without an app release.
 * Blocks sharing a place work independently.
 *
 * **The host owns the size**: give the block an explicit height. The content adapts to that
 * frame, so the host UI never jumps. While loading, the frame shows a placeholder — the SDK's
 * default one or the host's own ([setPlaceholderView]). When the place ends up without content the
 * block hides itself; a failure can be shown instead ([setErrorView]), an empty place cannot.
 *
 * ```xml
 * <cloud.mindbox.mobile_sdk.embedded.MindboxEmbeddedBlockView
 *     android:layout_width="match_parent"
 *     android:layout_height="120dp"
 *     app:mindboxPlaceSystemName="main-screen-top" />
 * ```
 *
 * The SDK owns the flow: content starts on attach, pauses on detach, reloads once per session.
 * The block owns its behavior too — visible while loading and while showing content, `GONE`
 * when the place ends up without content, unless a failure has [setErrorView] to show.
 * [setListener] only observes: callbacks arrive after the block already acted.
 */
public class MindboxEmbeddedBlockView internal constructor(
    context: Context,
    attrs: AttributeSet?,
    placeSystemName: String?,
    private val contentController: EmbeddedBlockContentController = EmbeddedBlockContentController(
        resolveFactory = { EmbeddedBlockContentFactory.resolve(context, placeSystemName.orNullIfBlank()) },
        placeSystemName = placeSystemName.orNullIfBlank(),
    ),
) : FrameLayout(context, attrs) {

    @JvmOverloads
    public constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : this(context, attrs, readPlaceSystemName(context, attrs))

    public constructor(
        context: Context,
        placeSystemName: String,
    ) : this(context, null, placeSystemName)

    public val placeSystemName: String? = placeSystemName.orNullIfBlank()
    private var listener: MindboxEmbeddedBlockListener = DefaultListener
    private var appearanceObserver: ((MindboxEmbeddedBlockAppearance) -> Unit)? = null
    private var placeholderView: View? = null
    private var errorView: View? = null
    private val defaultPlaceholder by lazy { EmbeddedBlockDefaultViews.placeholder(context) }
    private val mainHandler = Handler(Looper.getMainLooper())

    private enum class BlockEvent { LOADING, LOADED, FAILED }

    private var state: EmbeddedBlockState = EmbeddedBlockState.Loading
        set(value) {
            field = value
            applyState(value)
        }

    private var deliveredEvent: BlockEvent? = null
    private var isDeliveryScheduled = false
    private var shownContent: View? = null

    /** What the container shows right now: the one source for both its visibility and its report. */
    private var shownAppearance = MindboxEmbeddedBlockAppearance.PLACEHOLDER
    private var isContentStarted = false

    /** What the window callbacks last said. Attached and visible is the only combination that counts. */
    private var isWindowVisible = false

    /** Whether the host wrapper still shows the block. Nobody says otherwise until a wrapper does. */
    private var isHostVisible = true

    private var observedLifecycle: Lifecycle? = null

    private val hostDestroyObserver = object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            mindboxLogI("[EmbeddedBlock] Host screen destroyed, freeing content")
            detachFromHost()
            loggingRunCatching { contentController.release() }
        }
    }

    init {
        clipChildren = true
        clipToPadding = true
        setBackgroundColor(Color.TRANSPARENT)
        contentController.onStateChange = { newState -> state = newState }
        showContent(currentPlaceholder())
        warnIfPlaceIsMissing()
    }

    private fun warnIfPlaceIsMissing() {
        if (placeSystemName != null) return
        mindboxLogE(
            "[EmbeddedBlock] app:mindboxPlaceSystemName is not set on the block: it has nothing " +
                "to resolve and stays hidden. Set the attribute in XML, or create the block as " +
                "MindboxEmbeddedBlockView(context, placeSystemName).",
        )
    }

    public fun setListener(listener: MindboxEmbeddedBlockListener?) {
        val next = listener ?: DefaultListener
        // The same listener is not a new subscriber. A host rebinds it on every recycled row, and
        // replaying an outcome it already heard would have it rebuild its layout again — which
        // rebinds the listener again.
        if (next === this.listener) return

        this.listener = next
        if (listener == null) return
        // A new subscriber has heard nothing yet, so the current outcome is still news to it.
        deliveredEvent = null
        scheduleDelivery()
    }

    /**
     * Replaces the SDK's default loading placeholder. Fills the whole block frame.
     *
     * Takes effect immediately: a block that is loading right now swaps to the new placeholder.
     * Pass `null` to go back to the default one.
     */
    public fun setPlaceholderView(view: View?) {
        placeholderView = view
        if (state is EmbeddedBlockState.Loading) showContent(currentPlaceholder())
    }

    /**
     * The view for a block whose content failed. Setting it also keeps the block visible instead of
     * the default collapse. Fills the whole block frame.
     *
     * Applies to failures only. An empty place — one the config has nothing for — always collapses
     * and never shows this view: it is a request to keep the place through a failure, not permission
     * to fill a place that was never meant to be there.
     *
     * Applies from the next outcome on: a block that already collapsed stays collapsed until
     * its content reloads.
     */
    public fun setErrorView(view: View?) {
        errorView = view
    }

    /**
     * Reports how the block occupies its place, for wrappers that lay it out themselves instead of
     * letting the block set its own visibility — see [MindboxEmbeddedBlockAppearance].
     *
     * The current value arrives right away on subscribing: a wrapper that comes after the outcome
     * cannot miss what the block already decided.
     */
    @InternalMindboxApi
    public fun setAppearanceObserver(observer: ((MindboxEmbeddedBlockAppearance) -> Unit)?) {
        appearanceObserver = observer
        loggingRunCatching { observer?.invoke(shownAppearance) }
    }

    /**
     * Tells the block whether the host wrapper still shows it — a second source for the same input as
     * window visibility: the content runs while the window is visible **and** the wrapper says so.
     *
     * For wrappers whose whole app lives in one window. In Flutter every screen shares it, so leaving
     * a screen never takes the block out of the window: the block would keep waiting — and spending
     * its waiting budget — on a screen nobody is looking at, and could collapse before the user ever
     * reached it. `true` until a wrapper says otherwise, so a host that never calls this sees nothing
     * change.
     *
     * The semantics are exactly those of the window going away and coming back: a pause, not a reset.
     * A block hidden mid-load keeps the page it has and the remainder of its budget; shown again, it
     * counts that remainder down instead of starting the budget anew.
     */
    @InternalMindboxApi
    public fun setHostVisible(visible: Boolean) {
        if (isHostVisible == visible) return
        isHostVisible = visible
        mindboxLogI(
            "[EmbeddedBlock] Block (place='$placeSystemName') was " +
                "${if (visible) "shown" else "hidden"} by the host wrapper",
        )
        updateContentActivity()
    }

    private val hasCustomErrorView: Boolean
        get() = errorView != null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observeHostDestruction()
        isWindowVisible = windowVisibility == VISIBLE
        updateContentActivity()
    }

    override fun onDetachedFromWindow() {
        // Said explicitly rather than read back from `isAttachedToWindow`: the framework clears that
        // only after this call returns, so asking it here would answer that the view is still up.
        isWindowVisible = false
        updateContentActivity()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        isWindowVisible = visibility == VISIBLE
        updateContentActivity()
    }

    /** One switch out of two sources: nobody looks at the block unless both of them agree. */
    private fun updateContentActivity() {
        if (isWindowVisible && isHostVisible) startContent() else pauseContent()
    }

    private fun startContent() {
        if (isContentStarted) return
        isContentStarted = true
        mindboxLogI("[EmbeddedBlock] On screen (place='$placeSystemName'), starting content")
        loggingRunCatching { contentController.start() }
    }

    private fun pauseContent() {
        if (!isContentStarted) return
        isContentStarted = false
        mindboxLogI("[EmbeddedBlock] Off screen, pausing content")
        loggingRunCatching { contentController.pause() }
    }

    private fun observeHostDestruction(): Unit = loggingRunCatching {
        val lifecycle = findViewTreeLifecycleOwner()?.lifecycle ?: return@loggingRunCatching
        if (lifecycle === observedLifecycle) return@loggingRunCatching
        observedLifecycle?.removeObserver(hostDestroyObserver)
        observedLifecycle = lifecycle
        lifecycle.addObserver(hostDestroyObserver)
    }

    @InternalMindboxApi
    public fun release() {
        mindboxLogI("[EmbeddedBlock] Released by the host wrapper, freeing content")
        detachFromHost()
        loggingRunCatching { contentController.release() }
    }

    private fun detachFromHost(): Unit = loggingRunCatching {
        observedLifecycle?.removeObserver(hostDestroyObserver)
        observedLifecycle = null
        mainHandler.removeCallbacksAndMessages(null)
        isDeliveryScheduled = false
        listener = DefaultListener
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var touchDownX = 0f
    private var touchDownY = 0f

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = ev.x
                touchDownY = ev.y
                if (state is EmbeddedBlockState.Ready) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - touchDownX)
                val dy = abs(ev.y - touchDownY)
                if (dy > touchSlop && dy > dx) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun applyState(state: EmbeddedBlockState) {
        when (state) {
            is EmbeddedBlockState.Ready -> {
                val readyContent = contentController.contentView
                if (readyContent == null) {
                    mindboxLogW("[EmbeddedBlock] Ready content has no view, treating it as a failure")
                    this.state = EmbeddedBlockState.Failed
                    return
                }
                mindboxLogI("[EmbeddedBlock] Content ready")
                showContent(readyContent)
            }
            is EmbeddedBlockState.Loading -> {
                mindboxLogI("[EmbeddedBlock] Content loading, showing the placeholder")
                showContent(currentPlaceholder())
            }

            is EmbeddedBlockState.Empty -> {
                mindboxLogI("[EmbeddedBlock] Nothing to show for this place")
                // An empty place shows no error view, the host's own included: a custom error view is
                // a request to keep the place through a failure, not permission to fill a place the
                // config never meant to be there. So the block collapses, and the host reads it as
                // the same non-show a failure is.
                clearContent()
            }
            is EmbeddedBlockState.Failed -> {
                mindboxLogI("[EmbeddedBlock] Content failed, showing the error state")
                showErrorView()
            }
        }

        applyAppearance(appearanceFor(state))
        scheduleDelivery()
    }

    private fun appearanceFor(state: EmbeddedBlockState): MindboxEmbeddedBlockAppearance = when (state) {
        is EmbeddedBlockState.Loading -> MindboxEmbeddedBlockAppearance.PLACEHOLDER
        is EmbeddedBlockState.Ready -> MindboxEmbeddedBlockAppearance.CONTENT
        // A failure is shown only to a host that opted in explicitly; for the rest the block collapses.
        is EmbeddedBlockState.Failed ->
            if (hasCustomErrorView) {
                MindboxEmbeddedBlockAppearance.ERROR
            } else {
                MindboxEmbeddedBlockAppearance.COLLAPSED
            }
        is EmbeddedBlockState.Empty -> MindboxEmbeddedBlockAppearance.COLLAPSED
    }

    private fun applyAppearance(appearance: MindboxEmbeddedBlockAppearance) {
        shownAppearance = appearance
        visibility = if (appearance == MindboxEmbeddedBlockAppearance.COLLAPSED) GONE else VISIBLE
        loggingRunCatching { appearanceObserver?.invoke(appearance) }
    }

    private fun currentPlaceholder(): View = placeholderView ?: defaultPlaceholder

    private fun showErrorView() {
        val view = errorView
        if (view == null) clearContent() else showContent(view)
    }

    private fun showContent(content: View): Unit = loggingRunCatching {
        if (content === shownContent) return@loggingRunCatching
        shownContent?.let { removeView(it) }
        shownContent = content
        (content.parent as? ViewGroup)?.removeView(content)
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun clearContent() {
        shownContent?.let { removeView(it) }
        shownContent = null
    }

    private fun scheduleDelivery() {
        if (isDeliveryScheduled) return
        isDeliveryScheduled = true
        mainHandler.post { deliverPendingEvent() }
    }

    private fun deliverPendingEvent(): Unit = loggingRunCatching {
        isDeliveryScheduled = false
        val event = when {
            state is EmbeddedBlockState.Ready -> BlockEvent.LOADED
            state.nothingToShow -> BlockEvent.FAILED
            else -> BlockEvent.LOADING
        }
        if (event == deliveredEvent) return@loggingRunCatching

        deliveredEvent = event
        when (event) {
            BlockEvent.LOADED -> listener.onLoad(this)
            BlockEvent.FAILED -> listener.onFail(this)
            BlockEvent.LOADING -> Unit
        }
    }

    private companion object {
        private val DefaultListener = object : MindboxEmbeddedBlockListener {}
    }
}

private fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

private fun readPlaceSystemName(context: Context, attrs: AttributeSet?): String? {
    if (attrs == null) return null
    val values = context.obtainStyledAttributes(attrs, R.styleable.MindboxEmbeddedBlockView)
    return try {
        values.getString(R.styleable.MindboxEmbeddedBlockView_mindboxPlaceSystemName)
    } finally {
        values.recycle()
    }
}
