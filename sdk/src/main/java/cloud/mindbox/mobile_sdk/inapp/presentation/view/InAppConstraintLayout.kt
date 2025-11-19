package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.*
import cloud.mindbox.mobile_sdk.SnackbarPosition
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.isTop
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.px
import kotlin.math.abs

internal class InAppConstraintLayout : ConstraintLayout, BackButtonLayout {

    private var backButtonHandler: BackButtonHandler? = null

    fun setSwipeToDismissCallback(callback: () -> Unit) {
        swipeToDismissCallback = callback
    }

    private var swipeToDismissCallback: (() -> Unit)? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    companion object {
        private const val ANIM_DURATION = 500L
        private const val ANIM_SWIPE_DURATION = 100L
        private const val MODAL_WINDOW_MARGIN = 40
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun prepareLayoutForSnackbar(snackBarInAppType: InAppType.Snackbar) {
        val statusBarHeight = getStatusBarHeight()
        val navigationBarHeight = getNavigationBarHeight()

        updateLayoutParams<FrameLayout.LayoutParams> {
            when (snackBarInAppType.position.margin.kind) {
                InAppType.Snackbar.Position.Margin.MarginKind.DP -> {
                    when (snackBarInAppType.position.gravity.vertical) {
                        SnackbarPosition.TOP -> {
                            gravity = Gravity.TOP
                            setMargins(
                                snackBarInAppType.position.margin.left.px,
                                snackBarInAppType.position.margin.top.px + statusBarHeight,
                                snackBarInAppType.position.margin.right.px,
                                0
                            )
                        }
                        SnackbarPosition.BOTTOM -> {
                            gravity = Gravity.BOTTOM
                            setMargins(
                                snackBarInAppType.position.margin.left.px,
                                0,
                                snackBarInAppType.position.margin.right.px,
                                snackBarInAppType.position.margin.bottom.px + navigationBarHeight
                            )
                        }
                    }
                }
            }
        }

        var startingY = 0f
        doOnLayout { startingY = this.y }

        val self: View = this
        val gestureDetector =
            GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {

                var rightDY = 0f

                override fun onDown(e: MotionEvent): Boolean {
                    rightDY = self.y - e.rawY
                    self.parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    val tapTimeout: Int = ViewConfiguration.getLongPressTimeout()
                    val clickTime = e.eventTime - e.downTime
                    if (clickTime <= tapTimeout) {
                        this@InAppConstraintLayout.mindboxLogI("Click performed with duration = ${clickTime}ms.")
                        self.performClick()
                        return true
                    } else {
                        this@InAppConstraintLayout.mindboxLogI("Ignore long click with duration = ${clickTime}ms. Timeout = ${tapTimeout}ms.")
                        return false
                    }
                }

                override fun onScroll(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    distanceX: Float,
                    distanceY: Float
                ): Boolean {
                    val displacement: Float = if (snackBarInAppType.isTop()) {
                        minOf(e2.rawY + rightDY, startingY)
                    } else {
                        maxOf(e2.rawY + rightDY, startingY)
                    }
                    self.y = displacement
                    return true
                }
            }).apply {
                @Suppress("UsePropertyAccessSyntax")
                setIsLongpressEnabled(false)
            }

        setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)

            if (event.actionMasked == MotionEvent.ACTION_UP) {
                self.parent?.requestDisallowInterceptTouchEvent(false)
                if (abs(self.translationY) > (height / 2)) {
                    swipeToDismissCallback?.invoke()
                } else {
                    self.animate()
                        .y(startingY)
                        .setDuration(ANIM_SWIPE_DURATION)
                        .start()
                }
            }
            true
        }
    }

    @SuppressLint("ClickableViewAccessibility", "InternalInsetResource", "DiscouragedApi")
    private fun getNavigationBarHeight(): Int {
        var navigationBarHeight = 0
        val navBarResourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (navBarResourceId > 0) {
            navigationBarHeight = resources.getDimensionPixelSize(navBarResourceId)
        }
        return navigationBarHeight
    }

    @SuppressLint("ClickableViewAccessibility", "InternalInsetResource", "DiscouragedApi")
    private fun getStatusBarHeight(): Int {
        var statusBarHeight = 0
        val statusBarResourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (statusBarResourceId > 0) {
            statusBarHeight = resources.getDimensionPixelSize(statusBarResourceId)
        }
        return statusBarHeight
    }

    fun slideUp(isReverse: Boolean = false, onAnimationEnd: Runnable = Runnable { }) {
        val travelDistance = (height + marginBottom).toFloat()
        if (isReverse) {
            animate().translationY(travelDistance)
                .setDuration(ANIM_DURATION)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction(onAnimationEnd)
                .start()
        } else {
            translationY = travelDistance
            isVisible = true
            animate().translationY(0f)
                .setDuration(ANIM_DURATION)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction(onAnimationEnd)
                .start()
        }
    }

    fun slideDown(isReverse: Boolean = false, onAnimationEnd: Runnable = Runnable { }) {
        val travelDistance = -(height + marginTop).toFloat()
        if (isReverse) {
            animate().translationY(travelDistance)
                .setDuration(ANIM_DURATION)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction(onAnimationEnd)
                .start()
        } else {
            translationY = travelDistance
            isVisible = true
            animate().translationY(0f)
                .setDuration(ANIM_DURATION)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction(onAnimationEnd)
                .start()
        }
    }

    private fun prepareLayoutForWebView() {
        updateLayoutParams<MarginLayoutParams> {
            setMargins(0, 0, 0, 0)
        }
        updateLayoutParams<FrameLayout.LayoutParams> {
            gravity = Gravity.CENTER
            height = FrameLayout.LayoutParams.MATCH_PARENT
        }
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInset ->
            val inset = windowInset.getInsets(
                WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.displayCutout()
                    or WindowInsetsCompat.Type.ime()
            )

            view.updatePadding(
                bottom = maxOf(inset.bottom, getNavigationBarHeight())
            )
            mindboxLogI("Webview Insets: $inset")
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun prepareLayoutForModalWindow() {
        updateLayoutParams<MarginLayoutParams> {
            setMargins(
                MODAL_WINDOW_MARGIN.px,
                MODAL_WINDOW_MARGIN.px,
                MODAL_WINDOW_MARGIN.px,
                MODAL_WINDOW_MARGIN.px
            )
        }
        updateLayoutParams<FrameLayout.LayoutParams> {
            gravity = Gravity.CENTER
        }
    }

    fun prepareLayoutForInApp(inAppType: InAppType) {
        when (inAppType) {
            is InAppType.WebView -> prepareLayoutForWebView()
            is InAppType.ModalWindow -> prepareLayoutForModalWindow()
            is InAppType.Snackbar -> prepareLayoutForSnackbar(inAppType)
        }
    }

    constructor(
        context: Context,
        attrs: AttributeSet,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(
        context, attrs, defStyleAttr, defStyleRes
    )

    override fun setDismissListener(listener: OnClickListener?) {
        backButtonHandler = BackButtonHandler(this, listener)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        if (keyCode == KeyEvent.KEYCODE_BACK && backButtonHandler != null) {
            true
        } else {
            super.onKeyDown(keyCode, event)
        }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        val handled = backButtonHandler?.dispatchKeyEvent(event)
        return handled ?: super.dispatchKeyEvent(event)
    }
}
