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
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.utils.Constants
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import kotlin.math.abs

/**
 * A drop-in container for an embedded Mindbox block.
 *
 * The host marks a *place* by [placeSystemName] and never learns what goes into it — the mobile
 * config decides through an in-app with an `embedded` form variant bound to that place, and can
 * change it without an app release. Blocks sharing a place work independently.
 *
 * **The host owns the size**: give the block an explicit height. The content adapts to that
 * frame, so the host UI never jumps. While loading, the frame shows a placeholder — the SDK's
 * default one or the host's own ([setPlaceholderView]). When the place ends up without content the
 * block hides itself; a failure can be shown instead of hidden if the host gave it a view for one
 * ([setErrorView]).
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
 * when the place ends up without content: always for an empty place, and for a failure unless
 * [setErrorView] keeps it in place. A block that
 * has collapsed does not reopen its space for the retry's placeholder — only content expands it
 * back, so the host layout is not jerked on every pass across the screen.
 * [setListener] only observes: callbacks arrive after the block already acted.
 */
public class MindboxEmbeddedBlockView internal constructor(
    context: Context,
    attrs: AttributeSet?,
    placeSystemName: String?,
    private val contentController: EmbeddedBlockContentController = EmbeddedBlockContentController(
        placeSystemName = placeSystemName.orNullIfBlank(),
        configTimeout = readConfigTimeout(context, attrs),
        providerFactory = { content, attemptStartedAt ->
            EmbeddedBlockContentFactory.createProvider(context, content, attemptStartedAt)
        },
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

    /**
     * Whether the block has already settled on a place without content — collapsed, or showing the
     * host's error view.
     *
     * A settled block keeps what it shows. A retry — and the block gets one on every return to the
     * screen — is not a reload: the page behind the place is the one that already failed, so it
     * answers the same way, while the host watches its layout jerk by the block's height and a
     * placeholder flash on every pass across the screen, to show nothing in the end. That holds for
     * the error view just as much as for a collapse.
     *
     * Ended by content that actually appeared.
     */
    private var hasSettled = false

    /** What the block shows right now — the one source for both its own visibility and the report. */
    private var shownAppearance = MindboxEmbeddedBlockAppearance.PLACEHOLDER

    /**
     * Whether the window shows the block. Tracked rather than read off `windowVisibility`: the
     * callback carries the new value while the property still holds the old one.
     */
    private var isWindowVisible = false

    /** Whether the wrapper's host still shows the block. Nobody says otherwise until a wrapper does. */
    private var isHostVisible = true

    /** Whether a wrapper has released the block: then it stays stopped whatever the window says. */
    private var isReleased = false
    private var shownContent: View? = null
    private var isContentStarted = false
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
        if (shownAppearance == MindboxEmbeddedBlockAppearance.PLACEHOLDER) {
            showContent(currentPlaceholder())
        }
    }

    /**
     * The view for a block that failed. Setting it opts into showing the failure instead of the
     * default collapse: the block keeps its place and shows this view. Fills the whole block frame.
     *
     * Applies to failures only — an empty place always collapses, so a host cannot fill the space
     * of a block that was never meant to be there.
     *
     * A view set mid-failure swaps the error screen already shown, and `null` given while one is
     * shown takes the failure back down to a collapse — the space returns to the layout. What
     * neither does is expand a block that has already collapsed: reopening space the layout has
     * reclaimed would make it jump, so such a view takes effect on the next load.
     */
    public fun setErrorView(view: View?) {
        errorView = view
        // Re-decided rather than just swapped: whether a failure is shown at all depends on this
        // view existing, so taking it away has to collapse the block and say so, not leave the old
        // screen standing. What is on screen is the subject, not the state — a settled block goes on
        // showing its error view while the attempt it gets on the way back loads — and a block that
        // has already collapsed stays collapsed however this view changes.
        if (shownAppearance == MindboxEmbeddedBlockAppearance.ERROR) {
            applyState(state)
        }
    }

    /**
     * Reports how the block occupies its place, for wrappers that lay it out themselves instead of
     * relying on this view's own `visibility` — see [MindboxEmbeddedBlockAppearance].
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
     * Tells the block whether the host still shows it — a second source for the same input as
     * window visibility: the content runs while the window shows it and the host says so.
     *
     * For wrappers whose whole app lives in one window. In Flutter every screen shares it, so
     * leaving a screen never takes the block out of a window: the block would keep waiting — and
     * spending its budget — on a screen nobody is looking at, and could collapse before the user
     * ever got there. `true` by default, so a wrapper that says nothing behaves as before.
     *
     * A pause, not a reset: a block hidden mid-load keeps the page it has and the remainder of its
     * budget; shown again, it counts that remainder down instead of starting the budget anew.
     */
    @InternalMindboxApi
    public fun setHostVisible(visible: Boolean) {
        if (isHostVisible == visible) return

        isHostVisible = visible
        mindboxLogI(
            "[EmbeddedBlock] Block '$placeSystemName' was ${if (visible) "shown" else "hidden"} " +
                "by the host wrapper",
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
        isWindowVisible = false
        updateContentActivity()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        isWindowVisible = visibility == VISIBLE
        updateContentActivity()
    }

    /**
     * Whether anybody is looking at the block. The window answers that for a native host; a wrapper
     * whose app has a single window answers the rest through [setHostVisible], and a released block
     * is not looked at by definition.
     */
    private val isEffectivelyVisible: Boolean
        get() = isWindowVisible && isHostVisible && !isReleased

    /**
     * Three sources drive one switch — the window, the wrapper's host, a release — and each of them
     * can repeat what another has already said, so the switch is idempotent.
     */
    private fun updateContentActivity() {
        if (isEffectivelyVisible) startContent() else pauseContent()
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
        if (isReleased) return

        mindboxLogI("[EmbeddedBlock] Released by the host wrapper, freeing content")
        isReleased = true
        appearanceObserver = null
        // A released block is not looked at, whatever the window says: the switch hears that here,
        // or it would go on believing the content runs and pause a controller already released.
        updateContentActivity()
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
        if (state is EmbeddedBlockState.Ready && contentController.contentView == null) {
            mindboxLogW("[EmbeddedBlock] Ready content has no view, treating it as a failure")
            this.state = EmbeddedBlockState.Failed
            return
        }

        // Read before it is written: a settled block's answer for `Loading` is the appearance it
        // already had.
        val appearance = appearanceFor(state)
        shownAppearance = appearance
        hasSettled = when (appearance) {
            // A place without content, however it is drawn, is what the block settles on.
            MindboxEmbeddedBlockAppearance.COLLAPSED,
            MindboxEmbeddedBlockAppearance.ERROR,
            -> true
            // And content that actually appeared ends it: from here the block is an ordinary one
            // again, and the next load it really starts is entitled to its placeholder.
            MindboxEmbeddedBlockAppearance.CONTENT -> false
            MindboxEmbeddedBlockAppearance.PLACEHOLDER -> hasSettled
        }

        when (appearance) {
            MindboxEmbeddedBlockAppearance.PLACEHOLDER -> {
                mindboxLogI("[EmbeddedBlock] Content loading, showing the placeholder")
                showContent(currentPlaceholder())
            }
            // The guard above already turned a `Ready` without a view into a failure, so there is
            // one to show here; the null-safe call is what keeps that guarantee from being an `!!`.
            MindboxEmbeddedBlockAppearance.CONTENT -> {
                mindboxLogI("[EmbeddedBlock] Content ready")
                contentController.contentView?.let { showContent(it) }
            }
            MindboxEmbeddedBlockAppearance.ERROR -> {
                mindboxLogI("[EmbeddedBlock] Content failed, showing the host's error view")
                errorView?.let { showContent(it) }
            }
            MindboxEmbeddedBlockAppearance.COLLAPSED -> {
                mindboxLogI("[EmbeddedBlock] Nothing to show for this place, collapsing")
                clearContent()
            }
        }

        visibility = if (appearance == MindboxEmbeddedBlockAppearance.COLLAPSED) GONE else VISIBLE
        loggingRunCatching { appearanceObserver?.invoke(appearance) }
        scheduleDelivery()
    }

    private fun appearanceFor(state: EmbeddedBlockState): MindboxEmbeddedBlockAppearance =
        when (state) {
            // A block that already settled keeps what it shows even while it loads anew: a retry
            // earns neither the space the host has reclaimed nor a placeholder over the error view.
            // What it does not keep is an error view the host has taken away in the meantime —
            // there is nothing left to draw, so the block collapses.
            is EmbeddedBlockState.Loading -> when {
                !hasSettled -> MindboxEmbeddedBlockAppearance.PLACEHOLDER
                shownAppearance == MindboxEmbeddedBlockAppearance.ERROR && !hasCustomErrorView ->
                    MindboxEmbeddedBlockAppearance.COLLAPSED
                else -> shownAppearance
            }
            is EmbeddedBlockState.Ready -> MindboxEmbeddedBlockAppearance.CONTENT
            // A failure is shown only to those who opted in explicitly; for the rest it collapses.
            is EmbeddedBlockState.Failed ->
                if (hasCustomErrorView) {
                    MindboxEmbeddedBlockAppearance.ERROR
                } else {
                    MindboxEmbeddedBlockAppearance.COLLAPSED
                }
            // An empty place always collapses: a host cannot fill the space of a block that was
            // never meant to be there, however it drew its failures.
            is EmbeddedBlockState.Empty -> MindboxEmbeddedBlockAppearance.COLLAPSED
        }

    private fun currentPlaceholder(): View = placeholderView ?: defaultPlaceholder

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

private fun readConfigTimeout(context: Context, attrs: AttributeSet?): Milliseconds =
    loggingRunCatching(defaultValue = Constants.Embedded.defaultConfigTimeout) {
        val default = context.resources.getInteger(R.integer.mindbox_embedded_block_timeout_ms)
        if (attrs == null) return@loggingRunCatching Milliseconds(default.toLong())
        val values = context.obtainStyledAttributes(attrs, R.styleable.MindboxEmbeddedBlockView)
        try {
            Milliseconds(
                values.getInt(R.styleable.MindboxEmbeddedBlockView_mindboxTimeoutMs, default).toLong()
            )
        } finally {
            values.recycle()
        }
    }
