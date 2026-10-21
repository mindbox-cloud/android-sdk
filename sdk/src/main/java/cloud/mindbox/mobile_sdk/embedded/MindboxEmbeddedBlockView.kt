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
import androidx.fragment.app.Fragment
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
 * A container for an embedded Mindbox block.
 *
 * The host names a place with [placeSystemName] and gives the block an explicit height; the SDK
 * decides what the place shows. Content starts when the block enters the window, pauses when it
 * leaves, and is kept for a screen that goes to the back stack. With nothing to show the block
 * becomes `GONE`.
 *
 * ```xml
 * <cloud.mindbox.mobile_sdk.embedded.MindboxEmbeddedBlockView
 *     android:layout_width="match_parent"
 *     android:layout_height="120dp"
 *     app:mindboxPlaceSystemName="main-screen-top" />
 * ```
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
        providerFactory = { content, selectionTime ->
            EmbeddedBlockContentFactory.createProvider(context, content, selectionTime)
        },
        failureTracker = {
            loggingRunCatching(defaultValue = null) {
                Mindbox.initComponents(context)
                MindboxDI.appModule.inAppFailureTracker
            }
        },
        hostActivity = { loggingRunCatching(defaultValue = null) { context.findActivity() } },
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
     * Creates a block in code.
     *
     * @param placeSystemName The place the block fills. Whitespace around it is trimmed, letter
     * case is ignored.
     * @param timeoutMs How long the block waits for the SDK's answer before failing with
     * [MindboxEmbeddedBlockFailReason.NETWORK_ERROR]. `null` takes the
     * `mindbox_embedded_block_timeout_ms` integer resource, 30 s by default. Counted while the
     * screen with the block is shown.
     * @param loadingStrategy What the block shows until the SDK answers.
     * [MindboxEmbeddedBlockLoadingStrategy.AUTOMATIC] by default.
     * @param animatesReveal Whether the content is revealed with an animation. `true` by default.
     */
    @JvmOverloads
    public constructor(
        context: Context,
        placeSystemName: String,
        timeoutMs: Long? = null,
        loadingStrategy: MindboxEmbeddedBlockLoadingStrategy = MindboxEmbeddedBlockLoadingStrategy.AUTOMATIC,
        animatesReveal: Boolean = true,
    ) : this(context, null, placeSystemName, timeoutMs?.let(::Milliseconds), loadingStrategy, animatesReveal)

    /** The place the block fills, trimmed; `null` when none was given. */
    public val placeSystemName: String? = placeSystemName.orNullIfBlank()

    /** What the block shows until the SDK answers, given at creation. */
    public val loadingStrategy: MindboxEmbeddedBlockLoadingStrategy =
        loadingStrategy ?: readLoadingStrategy(context, attrs)

    /** Whether the content is revealed with an animation, given at creation. */
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
    private var isInWindow = false
    private var isScreenHidden = false
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

    private var hostFragment: Fragment? = null

    private val hostDestroyObserver = object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            val retainRefusal = tryRetainContent(destroyedOwner = owner)
            if (retainRefusal == null) {
                releaseViewOnly()
                return
            }
            mindboxLogI("[EmbeddedBlock] Lifecycle owner destroyed, freeing content ($retainRefusal)")
            detachFromHost()
            releaseContent()
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

    /** Sets the listener of the block's outcomes. A listener set after the outcome receives it at once. */
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
     * Replaces the SDK's shimmer with [view] while the content is loading; `null` restores the
     * shimmer. The view fills the block. To only recolor the shimmer, override the
     * `mindbox_embedded_block_shimmer_rest` and `mindbox_embedded_block_shimmer_highlight` color
     * resources.
     */
    public fun setPlaceholderView(view: View?) {
        placeholderView = view
        if (shownAppearance == MindboxEmbeddedBlockAppearance.PLACEHOLDER) {
            showContent(currentPlaceholder())
        }
    }

    /**
     * Shows [view] instead of collapsing the block when it fails; `null` restores the collapse.
     * The view fills the block. Applies to failures only, and only once the block has taken its
     * space: a block that waits hidden stays hidden.
     */
    public fun setErrorView(view: View?) {
        errorView = view
        if (shownAppearance == MindboxEmbeddedBlockAppearance.ERROR) {
            mindboxLogI("[EmbeddedBlock] Block '$placeSystemName': the host replaced the error view")
            applyState(state, isErrorViewSwap = true)
        }
    }

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
        isInWindow = true
        hostFragment = findHostFragment()
        screenOwnerAtAttach = loggingRunCatching(defaultValue = null) {
            findScreenOwner(explicitOwner = screenOwner, fragment = hostFragment)
        }
        reclaimRetainedContent()
        shownContent?.let(::placeContentInFrame)
        observeHostDestruction()
        isWindowVisible = windowVisibility == VISIBLE
        isScreenHidden = hostFragment.isHiddenWithParents()
        updateContentActivity()
    }

    override fun onDetachedFromWindow() {
        hostFragment = null
        isWindowVisible = false
        isInWindow = false
        updateContentActivity()
        cancelRevealAnimation()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        isWindowVisible = visibility == VISIBLE
        updateContentActivity()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        val isHidden = hostFragment.isHiddenWithParents()
        if (isHidden == isScreenHidden) return
        isScreenHidden = isHidden
        updateContentActivity()
    }

    private val isPlacedOnScreen: Boolean
        get() = isInWindow && isHostVisible && !isScreenHidden

    private val isEffectivelyVisible: Boolean
        get() = isWindowVisible && isPlacedOnScreen && !isReleased

    private fun updateContentActivity() {
        if (isEffectivelyVisible) startContent() else pauseContent()
        if (!isPlacedOnScreen) loggingRunCatching { contentController.onLeftScreen() }
    }

    private fun startContent() {
        if (isContentStarted) return
        isContentStarted = true
        hasStartedOnce = true
        mindboxLogI("[EmbeddedBlock] Visible in window (place='$placeSystemName'), starting content")
        loggingRunCatching { contentController.start() }
    }

    private fun pauseContent() {
        if (!isContentStarted) return
        isContentStarted = false
        if (isReleased) {
            mindboxLogI("[EmbeddedBlock] Block '$placeSystemName' was released by the host, stopping content")
        } else {
            mindboxLogI("[EmbeddedBlock] Not visible in window (place='$placeSystemName'), pausing content")
        }
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
     * Releases the block and its content. A released block shows nothing again. Needed only when
     * the block is discarded before its screen is destroyed.
     */
    public fun release() {
        if (isReleased) return

        mindboxLogI("[EmbeddedBlock] Released by the host, freeing content")
        isReleased = true
        appearanceObserver = null
        updateContentActivity()
        detachFromHost()
        releaseContent()
    }

    private fun releaseContent(): Unit = loggingRunCatching {
        contentController.heldOutcome?.let(::updatePlaceMemory)
        contentController.release()
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
        if (isDeliveryScheduled) deliverPendingEvent()
        if (shownAppearance == MindboxEmbeddedBlockAppearance.CONTENT) clearContent() else cancelRevealAnimation()
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
            mindboxLogI("[EmbeddedBlock] Keeping the content for the screen (place='$place')")
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

    private fun applyState(state: EmbeddedBlockState, isErrorViewSwap: Boolean = false) {
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
                if (state is EmbeddedBlockState.Loading) {
                    mindboxLogI("[EmbeddedBlock] Block '$placeSystemName': content loading behind the host's error view")
                } else if (!isErrorViewSwap) {
                    mindboxLogI("[EmbeddedBlock] Block '$placeSystemName': content failed — showing the host's error view")
                }
                errorView?.let { showContent(it) }
            }
            MindboxEmbeddedBlockAppearance.COLLAPSED -> {
                when (state) {
                    is EmbeddedBlockState.Loading -> mindboxLogI("[EmbeddedBlock] Block '$placeSystemName': content loading, staying hidden")
                    is EmbeddedBlockState.Failed -> mindboxLogI("[EmbeddedBlock] Block '$placeSystemName': content failed — collapsing")
                    else -> mindboxLogI("[EmbeddedBlock] Block '$placeSystemName': the place came out empty — collapsing")
                }
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
        fadingOutView?.let { faded ->
            removeView(faded)
            faded.alpha = 1f
        }
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
        fadeAnimator = revealAnimation.crossfade(content, previous) {
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
