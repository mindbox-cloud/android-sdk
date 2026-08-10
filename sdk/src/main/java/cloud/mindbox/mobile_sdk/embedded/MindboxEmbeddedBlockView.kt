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
 * block hides itself, unless the host gave it a view to show instead ([setErrorView]).
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
 * when the place ends up without content, unless [setErrorView] keeps it in place.
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
    private var visibilityObserver: ((Boolean) -> Unit)? = null
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
        this.listener = listener ?: DefaultListener
        if (listener == null) return
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
     * The view for a place that ended up without content. Setting it also keeps the block
     * visible instead of the default collapse. Fills the whole block frame.
     *
     * Applies from the next outcome on: a block that already collapsed stays collapsed until
     * its content reloads.
     */
    public fun setErrorView(view: View?) {
        errorView = view
    }

    @InternalMindboxApi
    public fun setVisibilityObserver(observer: ((isVisible: Boolean) -> Unit)?) {
        visibilityObserver = observer
    }

    private val hasCustomErrorView: Boolean
        get() = errorView != null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observeHostDestruction()
        if (windowVisibility == VISIBLE) startContent()
    }

    override fun onDetachedFromWindow() {
        pauseContent()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) startContent() else pauseContent()
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
                showErrorView()
            }
            is EmbeddedBlockState.Failed -> {
                mindboxLogI("[EmbeddedBlock] Content failed, showing the error state")
                showErrorView()
            }
        }

        applyDefaultVisibility(state)
        scheduleDelivery()
    }

    private fun applyDefaultVisibility(state: EmbeddedBlockState) {
        val isVisible = when {
            state.nothingToShow -> hasCustomErrorView
            else -> true
        }
        visibility = if (isVisible) VISIBLE else GONE
        loggingRunCatching { visibilityObserver?.invoke(isVisible) }
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
