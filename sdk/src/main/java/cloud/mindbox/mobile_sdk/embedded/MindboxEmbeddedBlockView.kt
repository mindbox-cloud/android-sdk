package cloud.mindbox.mobile_sdk.embedded

import android.animation.Animator
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.Rect
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
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.R
import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.findActivity
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.managers.SharedPreferencesManager
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.PlaceKey
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
 * The place name is matched like an operation system name: surrounding whitespace is trimmed and
 * letter case is ignored, so `Main-Screen-Top` and `main-screen-top` are the same place, not two.
 *
 * **The host owns the size**: give the block an explicit height. The content adapts to that
 * frame. What the block shows before the SDK answers is decided by [loadingStrategy]: a
 * placeholder — the SDK's default one or the host's own ([setPlaceholderView]) — nothing, or — by
 * default — nothing until the place has shown content once on this device and a placeholder from
 * then on. A block that takes its space up front keeps the layout still at the price of flashing
 * where there is nothing to show; a block that waits hidden never flashes at the price of the
 * layout growing when content arrives. A place that always has a campaign behind it is worth an
 * explicit [MindboxEmbeddedBlockLoadingStrategy.PLACEHOLDER]. When the place ends up without
 * content the block hides itself; a failure can be shown instead of hidden if the host gave it a
 * view for one ([setErrorView]).
 *
 * The content is revealed with the SDK's own animation — it fades in, and a block that started
 * hidden grows to its height — unless [animatesReveal] is off. A host that wants an animation of
 * its own turns that off, puts the block in a container of its own and animates the container in
 * [MindboxEmbeddedBlockListener.onLoad].
 *
 * ```xml
 * <cloud.mindbox.mobile_sdk.embedded.MindboxEmbeddedBlockView
 *     android:layout_width="match_parent"
 *     android:layout_height="120dp"
 *     app:mindboxPlaceSystemName="main-screen-top" />
 * ```
 *
 * The SDK owns the flow: content starts on attach, pauses on detach, reloads once per session.
 * A screen that leaves the window for the back stack — a Fragment behind a newer one, a Compose
 * destination behind the next — keeps the block's content, and the block the screen builds on its
 * return shows it at once. The content goes when the screen goes.
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
    configTimeout: Milliseconds? = null,
    loadingStrategy: MindboxEmbeddedBlockLoadingStrategy? = null,
    animatesReveal: Boolean? = null,
    contentController: EmbeddedBlockContentController = EmbeddedBlockContentController(
        placeSystemName = placeSystemName.orNullIfBlank(),
        configTimeout = configTimeout ?: readConfigTimeout(context, attrs),
        providerFactory = { content, attemptStartedAt ->
            EmbeddedBlockContentFactory.createProvider(context, content, attemptStartedAt)
        },
        failureTracker = {
            loggingRunCatching(defaultValue = null) {
                Mindbox.initComponents(context)
                MindboxDI.appModule.inAppFailureTracker
            }
        },
    ),
    private val contentStore: () -> EmbeddedBlockContentStore? = {
        loggingRunCatching(defaultValue = null) {
            if (MindboxDI.isInitialized()) MindboxDI.appModule.embeddedBlockContentStore else null
        }
    },
    private val placeMemory: EmbeddedBlockPlaceMemory = EmbeddedBlockPlaceMemory(),
    private val revealAnimation: EmbeddedBlockRevealAnimation = EmbeddedBlockRevealAnimation(),
) : FrameLayout(context, attrs) {

    private var contentController: EmbeddedBlockContentController = contentController

    @JvmOverloads
    public constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : this(context, attrs, readPlaceSystemName(context, attrs))

    /**
     * Creates a block for [placeSystemName] in code, where there is no XML to carry the attributes.
     *
     * @param placeSystemName The place this block fills, as named in the mobile config. Matched
     * with surrounding whitespace trimmed and letter case ignored, the same way an operation system
     * name is.
     * @param timeoutMs How long the block waits to learn what it shows before failing with
     * [MindboxEmbeddedBlockFailReason.NETWORK_ERROR] — [MindboxEmbeddedBlockListener.onFail], and
     * [setErrorView] applies — in milliseconds; the same budget `app:mindboxTimeoutMs` sets from
     * XML. `null` means the SDK default of 30 s. An answer that arrives after that no longer
     * expands the block; the next attempt starts when the block enters the window again.
     * @param loadingStrategy What the block shows until the SDK answers — the same choice
     * `app:mindboxLoadingStrategy` makes from XML. [MindboxEmbeddedBlockLoadingStrategy.AUTOMATIC] —
     * the default — keeps the block hidden until the place has shown content once on this device
     * and puts a placeholder there from then on.
     * @param animatesReveal Whether the SDK animates the reveal of the content — a fade, and the
     * growth of a block that waited hidden; the same flag `app:mindboxAnimatesReveal` sets from
     * XML. `true` by default. Turn it off to animate the block's container yourself in
     * [MindboxEmbeddedBlockListener.onLoad].
     */
    @JvmOverloads
    public constructor(
        context: Context,
        placeSystemName: String,
        timeoutMs: Long? = null,
        loadingStrategy: MindboxEmbeddedBlockLoadingStrategy = MindboxEmbeddedBlockLoadingStrategy.AUTOMATIC,
        animatesReveal: Boolean = true,
    ) : this(context, null, placeSystemName, timeoutMs?.let(::Milliseconds), loadingStrategy, animatesReveal)

    public val placeSystemName: String? = placeSystemName.orNullIfBlank()

    /**
     * What the block shows until the SDK answers, given at creation.
     * See [MindboxEmbeddedBlockLoadingStrategy].
     */
    public val loadingStrategy: MindboxEmbeddedBlockLoadingStrategy =
        loadingStrategy ?: readLoadingStrategy(context, attrs)

    /**
     * Whether the SDK animates the reveal of the content, given at creation. `false` swaps the
     * looks and applies the space at once — for a host that animates the block's container itself.
     */
    public val animatesReveal: Boolean = animatesReveal ?: readAnimatesReveal(context, attrs)

    private var listener: MindboxEmbeddedBlockListener = DefaultListener
    private var appearanceObserver: ((MindboxEmbeddedBlockAppearance) -> Unit)? = null
    private var placeholderView: View? = null
    private var errorView: View? = null
    private val defaultPlaceholder by lazy { EmbeddedBlockDefaultViews.placeholder(context) }
    private val mainHandler = Handler(Looper.getMainLooper())

    private val contentFrame = Rect()

    private enum class BlockEvent { LOADED, EMPTY, FAILED }

    private var state: EmbeddedBlockState = EmbeddedBlockState.Loading
        set(value) {
            field = value
            applyState(value)
        }

    private var deliveredEvent: BlockEvent? = null
    private var isDeliveryScheduled = false
    private var hasSettled = false
    private var shownAppearance = MindboxEmbeddedBlockAppearance.PLACEHOLDER
    private var isWindowVisible = false
    private var isHostVisible = true
    private var isReleased = false
    private var shownContent: View? = null
    private var fadingOutView: View? = null
    private var fadeAnimator: Animator? = null
    private var growthAnimator: Animator? = null
    private var growthTargetHeight: Int? = null
    private var isReclaimingContent = false
    private var isContentStarted = false
    private var hasStartedOnce = false
    private var observedLifecycle: Lifecycle? = null

    private var screenOwner: LifecycleOwner? = null

    private var screenOwnerAtAttach: LifecycleOwner? = null

    private val hostDestroyObserver = object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            val retainRefusal = tryRetainContent(destroyedOwner = owner)
            if (retainRefusal == null) {
                releaseViewOnly()
                return
            }
            mindboxLogI("[EmbeddedBlock] Host screen destroyed, freeing content ($retainRefusal)")
            detachFromHost()
            loggingRunCatching { contentController.release() }
        }
    }

    init {
        clipChildren = true
        clipToPadding = true
        setBackgroundColor(Color.TRANSPARENT)
        contentController.onStateChange = { newState -> state = newState }
        loggingRunCatching { SharedPreferencesManager.with(context) }
        val initial = initialAppearanceFor(this.loadingStrategy) {
            placeMemory.hasShownContentAt(placeSystemName)
        }
        shownAppearance = initial
        hasSettled = initial == MindboxEmbeddedBlockAppearance.COLLAPSED
        if (initial == MindboxEmbeddedBlockAppearance.PLACEHOLDER) {
            showContent(currentPlaceholder())
        } else {
            visibility = GONE
        }
        logInitialLook()
        warnIfPlaceIsMissing()
    }

    private fun logInitialLook() {
        val look =
            if (shownAppearance == MindboxEmbeddedBlockAppearance.COLLAPSED) {
                "hidden, taking no space"
            } else {
                "a placeholder"
            }
        mindboxLogI(
            "[EmbeddedBlock] Block '$placeSystemName' starts with $look (strategy $loadingStrategy)",
        )
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
        if (isReleased) return

        val next = listener ?: DefaultListener
        if (next === this.listener) return

        this.listener = next
        if (listener == null) return
        deliveredEvent = null
        scheduleDelivery()
    }

    /**
     * Replaces the SDK's default loading placeholder. Fills the whole block frame.
     *
     * Takes effect immediately: a block that is loading right now swaps to the new placeholder.
     * Pass `null` to go back to the default one. A block that waits hidden — see
     * [loadingStrategy] — shows no placeholder at all.
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
     * reclaimed would make it jump, so such a view takes effect on a load that starts the cycle
     * anew, never on the silent retry a return to the screen brings. For the same reason a block
     * that waits hidden — see [loadingStrategy] — shows no error screen while it has not taken its
     * space; once it has shown content, a failure applies here like anywhere else.
     */
    public fun setErrorView(view: View?) {
        errorView = view
        if (shownAppearance == MindboxEmbeddedBlockAppearance.ERROR) {
            applyState(state)
        }
    }

    /**
     * Reports how the block occupies its place, for wrappers that lay it out themselves instead of
     * relying on this view's own `visibility` — see [MindboxEmbeddedBlockAppearance].
     *
     * The current value arrives right away on subscribing: a wrapper that comes after the outcome
     * cannot miss what the block already decided. Whether the change deserves an animation is
     * [isRevealAnimated] at the moment the observer runs — the view is the one owner of that
     * decision, gates included, so a wrapper animating its own frame never disagrees with it.
     */
    @InternalMindboxApi
    public fun setAppearanceObserver(observer: ((MindboxEmbeddedBlockAppearance) -> Unit)?) {
        if (isReleased) return

        appearanceObserver = observer
        isRevealAnimated = false
        loggingRunCatching { observer?.invoke(shownAppearance) }
    }

    @InternalMindboxApi
    public var isRevealAnimated: Boolean = false
        private set

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

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        withContentFrame(right - left, bottom - top) { frame -> shownContent?.measureTo(frame) }
        super.onLayout(changed, left, top, right, bottom)
    }

    override fun setPadding(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        super.setPadding(left, top, right, bottom)
        shownContent?.let(::placeContentInFrame)
    }

    override fun setPaddingRelative(
        start: Int,
        top: Int,
        end: Int,
        bottom: Int
    ) {
        super.setPaddingRelative(start, top, end, bottom)
        shownContent?.let(::placeContentInFrame)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        screenOwnerAtAttach = loggingRunCatching(defaultValue = null) { findScreenOwner(explicitOwner = screenOwner) }
        reclaimRetainedContent()
        shownContent?.let(::placeContentInFrame)
        observeHostDestruction()
        isWindowVisible = windowVisibility == VISIBLE
        updateContentActivity()
    }

    override fun onDetachedFromWindow() {
        isWindowVisible = false
        updateContentActivity()
        cancelRevealAnimation()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        isWindowVisible = visibility == VISIBLE
        updateContentActivity()
    }

    private val isEffectivelyVisible: Boolean
        get() = isWindowVisible && isHostVisible && !isReleased

    private fun updateContentActivity() {
        if (isEffectivelyVisible) startContent() else pauseContent()
    }

    private fun startContent() {
        if (isContentStarted) return
        isContentStarted = true
        hasStartedOnce = true
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
        if (isReleased) return@loggingRunCatching
        val lifecycle = findViewTreeLifecycleOwner()?.lifecycle ?: return@loggingRunCatching
        if (lifecycle === observedLifecycle) return@loggingRunCatching
        observedLifecycle?.removeObserver(hostDestroyObserver)
        observedLifecycle = lifecycle
        lifecycle.addObserver(hostDestroyObserver)
    }

    /**
     * Releases the block and everything it holds. One way only: a released block never shows
     * content again — to show the place again, create a new block.
     *
     * Call it when the block is discarded before its screen is destroyed; otherwise its resources
     * live until then. A block that only leaves the window needs no call.
     */
    public fun release() {
        if (isReleased) return

        mindboxLogI("[EmbeddedBlock] Released by the host, freeing content")
        isReleased = true
        appearanceObserver = null
        updateContentActivity()
        detachFromHost()
        loggingRunCatching { contentController.release() }
    }

    @InternalMindboxApi
    public fun setScreenOwner(owner: LifecycleOwner?) {
        screenOwner = owner
    }

    @InternalMindboxApi
    public fun releaseOrRetain() {
        if (isReleased) return

        val retainRefusal = tryRetainContent(destroyedOwner = null)
        if (retainRefusal == null) {
            mindboxLogI("[EmbeddedBlock] Let go by the host wrapper, the content stays with the screen")
            releaseViewOnly()
            return
        }
        mindboxLogI("[EmbeddedBlock] Let go by the host wrapper, nothing to keep ($retainRefusal)")
        release()
    }

    private fun releaseViewOnly() {
        isReleased = true
        appearanceObserver = null
        detachFromHost()
    }

    private fun detachFromHost(): Unit = loggingRunCatching {
        cancelRevealAnimation()
        observedLifecycle?.removeObserver(hostDestroyObserver)
        observedLifecycle = null
        screenOwnerAtAttach = null
        mainHandler.removeCallbacksAndMessages(null)
        isDeliveryScheduled = false
        listener = DefaultListener
    }

    private fun tryRetainContent(destroyedOwner: LifecycleOwner?): String? =
        loggingRunCatching(defaultValue = "keeping the content failed") {
            val place = placeSystemName ?: return@loggingRunCatching "no place"
            if (!contentController.isRetainable) return@loggingRunCatching "no content to keep"
            val store = contentStore() ?: return@loggingRunCatching "the SDK is not initialized"
            val activity = context.findActivity()
            if (activity != null && (activity.isFinishing || activity.isChangingConfigurations)) {
                return@loggingRunCatching "the activity is going away"
            }
            val owner = screenOwnerAtAttach?.takeIf { attached -> attached.isScreenOwnerFor(destroyedOwner) }
                ?: findScreenOwner(destroyedOwner, explicitOwner = screenOwner)
                ?: return@loggingRunCatching "no screen outlives the view"
            if (owner.lifecycle.currentState == Lifecycle.State.RESUMED) {
                return@loggingRunCatching "the screen is still in front"
            }

            pauseContent()
            contentController.onStateChange = null
            clearContent()
            store.retain(owner, PlaceKey.of(place), contentController, activity)
            mindboxLogI("[EmbeddedBlock] Host view destroyed, keeping the content for the screen (place='$place')")
            null
        }

    private fun reclaimRetainedContent(): Unit = loggingRunCatching {
        if (isReleased || hasStartedOnce) return@loggingRunCatching
        val place = placeSystemName ?: return@loggingRunCatching
        val store = contentStore() ?: return@loggingRunCatching
        val owner = screenOwnerAtAttach ?: return@loggingRunCatching
        val kept = store.reclaim(owner, PlaceKey.of(place), context.findActivity()) ?: return@loggingRunCatching

        contentController.onStateChange = null
        contentController = kept
        kept.onStateChange = { newState -> state = newState }
        mindboxLogI("[EmbeddedBlock] Back on the screen (place='$place'), showing the kept content")
        isReclaimingContent = true
        try {
            kept.lastReportedState?.let { keptState -> state = keptState }
        } finally {
            isReclaimingContent = false
        }
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
        val previous = shownAppearance
        val appearance = appearanceFor(state)
        shownAppearance = appearance
        updatePlaceMemory(state)
        hasSettled = when (appearance) {
            MindboxEmbeddedBlockAppearance.COLLAPSED,
            MindboxEmbeddedBlockAppearance.ERROR,
            -> true
            MindboxEmbeddedBlockAppearance.CONTENT -> false
            MindboxEmbeddedBlockAppearance.PLACEHOLDER -> hasSettled
        }

        cancelRevealAnimation()
        val reveals = appearance == MindboxEmbeddedBlockAppearance.CONTENT &&
            previous != MindboxEmbeddedBlockAppearance.CONTENT
        val animated = reveals && shouldAnimateReveal
        isRevealAnimated = animated

        when (appearance) {
            MindboxEmbeddedBlockAppearance.PLACEHOLDER -> {
                mindboxLogI("[EmbeddedBlock] Content loading, showing the placeholder")
                showContent(currentPlaceholder())
            }
            MindboxEmbeddedBlockAppearance.CONTENT -> {
                mindboxLogI("[EmbeddedBlock] Content ready")
                contentController.contentView?.let { showContent(it, animated) }
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
        if (animated && previous == MindboxEmbeddedBlockAppearance.COLLAPSED && appearanceObserver == null) {
            animateGrowth()
        }
        loggingRunCatching { appearanceObserver?.invoke(appearance) }
        scheduleDelivery()
    }

    private val shouldAnimateReveal: Boolean
        get() = animatesReveal && !isReclaimingContent && isAttachedToWindow &&
            loggingRunCatching(defaultValue = false) { revealAnimation.isEnabled }

    private fun updatePlaceMemory(state: EmbeddedBlockState) {
        val place = placeSystemName ?: return
        loggingRunCatching {
            when (state) {
                EmbeddedBlockState.Ready -> placeMemory.rememberShownContent(PlaceKey.of(place))
                EmbeddedBlockState.Empty -> placeMemory.forgetPlace(PlaceKey.of(place))
                EmbeddedBlockState.Loading, is EmbeddedBlockState.Failed -> Unit
            }
        }
    }

    private fun animateGrowth() {
        val params = layoutParams ?: return
        val target = params.height
        if (target <= 0) return
        growthTargetHeight = target
        params.height = 0
        requestLayout()
        growthAnimator = revealAnimation.growHeight(this, target) {
            growthAnimator = null
            growthTargetHeight = null
        }
    }

    private fun cancelRevealAnimation() {
        growthAnimator?.let { animator ->
            growthAnimator = null
            animator.cancel()
        }
        growthTargetHeight = null
        fadeAnimator?.let { animator ->
            fadeAnimator = null
            animator.cancel()
        }
        settleFadingContent()
    }

    private fun settleFadingContent() {
        fadingOutView?.let { removeView(it) }
        fadingOutView = null
    }

    private fun appearanceFor(state: EmbeddedBlockState): MindboxEmbeddedBlockAppearance =
        when (state) {
            is EmbeddedBlockState.Loading -> when {
                !hasSettled -> MindboxEmbeddedBlockAppearance.PLACEHOLDER
                shownAppearance == MindboxEmbeddedBlockAppearance.ERROR && !hasCustomErrorView ->
                    MindboxEmbeddedBlockAppearance.COLLAPSED
                else -> shownAppearance
            }
            is EmbeddedBlockState.Ready -> MindboxEmbeddedBlockAppearance.CONTENT
            is EmbeddedBlockState.Failed -> when {
                !hasSettled ->
                    if (hasCustomErrorView) {
                        MindboxEmbeddedBlockAppearance.ERROR
                    } else {
                        MindboxEmbeddedBlockAppearance.COLLAPSED
                    }
                shownAppearance == MindboxEmbeddedBlockAppearance.ERROR && !hasCustomErrorView ->
                    MindboxEmbeddedBlockAppearance.COLLAPSED
                else -> shownAppearance
            }
            is EmbeddedBlockState.Empty -> MindboxEmbeddedBlockAppearance.COLLAPSED
        }

    private fun currentPlaceholder(): View = placeholderView ?: defaultPlaceholder

    private fun showContent(content: View, animated: Boolean = false): Unit = loggingRunCatching {
        if (content === shownContent) return@loggingRunCatching
        val previous = shownContent
        shownContent = content
        (content.parent as? ViewGroup)?.removeView(content)
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        placeContentInFrame(content)
        if (!animated) {
            previous?.let { removeView(it) }
            return@loggingRunCatching
        }
        fadingOutView = previous
        fadeAnimator = revealAnimation.fadeIn(content) {
            fadeAnimator = null
            settleFadingContent()
        }
    }

    private fun placeContentInFrame(content: View) {
        withContentFrame(width, height) { frame -> content.placeIn(frame) }
    }

    private inline fun withContentFrame(blockWidth: Int, blockHeight: Int, action: (Rect) -> Unit) {
        contentFrame.set(
            paddingLeft,
            paddingTop,
            blockWidth - paddingRight,
            (growthTargetHeight ?: blockHeight) - paddingBottom,
        )
        if (contentFrame.isEmpty) return
        action(contentFrame)
    }

    private fun View.placeIn(frame: Rect) {
        if (left == frame.left && top == frame.top && right == frame.right && bottom == frame.bottom) {
            return
        }
        measureTo(frame)
        layout(frame.left, frame.top, frame.right, frame.bottom)
    }

    private fun View.measureTo(frame: Rect) {
        if (measuredWidth == frame.width() && measuredHeight == frame.height()) return
        measure(
            MeasureSpec.makeMeasureSpec(frame.width(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(frame.height(), MeasureSpec.EXACTLY),
        )
    }

    private fun clearContent() {
        cancelRevealAnimation()
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
        // Loading is not an outcome, so the record of what was delivered is left alone: writing it
        // down would make the outcome that follows look new even when it is the one already heard.
        when (val current = state) {
            EmbeddedBlockState.Loading -> Unit
            EmbeddedBlockState.Ready -> deliverIfChanged(BlockEvent.LOADED) { view -> onLoad(view) }
            EmbeddedBlockState.Empty -> deliverIfChanged(BlockEvent.EMPTY) { view -> onEmpty(view) }
            is EmbeddedBlockState.Failed -> deliverIfChanged(BlockEvent.FAILED) { view -> onFail(view, current.reason) }
        }
    }

    private inline fun deliverIfChanged(
        event: BlockEvent,
        deliver: MindboxEmbeddedBlockListener.(MindboxEmbeddedBlockView) -> Unit,
    ) {
        if (event == deliveredEvent) return
        deliveredEvent = event
        listener.deliver(this)
    }

    public companion object {
        private val DefaultListener = object : MindboxEmbeddedBlockListener {}

        @InternalMindboxApi
        public val REVEAL_ANIMATION_DURATION_MS: Long = Constants.Embedded.revealAnimationDuration.interval

        @InternalMindboxApi
        public fun initialAppearance(
            context: Context,
            placeSystemName: String?,
            loadingStrategy: MindboxEmbeddedBlockLoadingStrategy,
        ): MindboxEmbeddedBlockAppearance {
            loggingRunCatching { SharedPreferencesManager.with(context) }
            return initialAppearanceFor(loadingStrategy) {
                EmbeddedBlockPlaceMemory().hasShownContentAt(placeSystemName)
            }
        }

        internal fun initialAppearanceFor(
            strategy: MindboxEmbeddedBlockLoadingStrategy,
            hasShownContentBefore: () -> Boolean,
        ): MindboxEmbeddedBlockAppearance = when (strategy) {
            MindboxEmbeddedBlockLoadingStrategy.PLACEHOLDER -> MindboxEmbeddedBlockAppearance.PLACEHOLDER
            MindboxEmbeddedBlockLoadingStrategy.HIDDEN -> MindboxEmbeddedBlockAppearance.COLLAPSED
            MindboxEmbeddedBlockLoadingStrategy.AUTOMATIC ->
                if (hasShownContentBefore()) {
                    MindboxEmbeddedBlockAppearance.PLACEHOLDER
                } else {
                    MindboxEmbeddedBlockAppearance.COLLAPSED
                }
        }
    }
}

private fun String?.orNullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun EmbeddedBlockPlaceMemory.hasShownContentAt(place: String?): Boolean {
    val name = place.orNullIfBlank() ?: return false
    return loggingRunCatching(defaultValue = false) { hasShownContent(PlaceKey.of(name)) }
}

private fun <T> readBlockAttribute(
    context: Context,
    attrs: AttributeSet?,
    defaultValue: T,
    read: (TypedArray) -> T,
): T = loggingRunCatching(defaultValue = defaultValue) {
    if (attrs == null) return@loggingRunCatching defaultValue
    val values = context.obtainStyledAttributes(attrs, R.styleable.MindboxEmbeddedBlockView)
    try {
        read(values)
    } finally {
        values.recycle()
    }
}

private fun readPlaceSystemName(context: Context, attrs: AttributeSet?): String? =
    readBlockAttribute(context, attrs, defaultValue = null) { values ->
        values.getString(R.styleable.MindboxEmbeddedBlockView_mindboxPlaceSystemName)
    }

private fun readConfigTimeout(context: Context, attrs: AttributeSet?): Milliseconds {
    val default = loggingRunCatching(defaultValue = Constants.Embedded.defaultConfigTimeout.interval.toInt()) {
        context.resources.getInteger(R.integer.mindbox_embedded_block_timeout_ms)
    }
    val timeoutMs = readBlockAttribute(context, attrs, defaultValue = default) { values ->
        values.getInt(R.styleable.MindboxEmbeddedBlockView_mindboxTimeoutMs, default)
    }
    return Milliseconds(timeoutMs.toLong())
}

private fun readLoadingStrategy(context: Context, attrs: AttributeSet?): MindboxEmbeddedBlockLoadingStrategy =
    readBlockAttribute(context, attrs, defaultValue = MindboxEmbeddedBlockLoadingStrategy.AUTOMATIC) { values ->
        when (values.getInt(R.styleable.MindboxEmbeddedBlockView_mindboxLoadingStrategy, 0)) {
            1 -> MindboxEmbeddedBlockLoadingStrategy.PLACEHOLDER
            2 -> MindboxEmbeddedBlockLoadingStrategy.HIDDEN
            else -> MindboxEmbeddedBlockLoadingStrategy.AUTOMATIC
        }
    }

private fun readAnimatesReveal(context: Context, attrs: AttributeSet?): Boolean =
    readBlockAttribute(context, attrs, defaultValue = true) { values ->
        values.getBoolean(R.styleable.MindboxEmbeddedBlockView_mindboxAnimatesReveal, true)
    }
