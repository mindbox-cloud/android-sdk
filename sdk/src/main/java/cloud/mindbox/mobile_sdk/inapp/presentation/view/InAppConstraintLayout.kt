package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.*
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import cloud.mindbox.mobile_sdk.SnackbarPosition
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.isTop
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
        private const val MODAL_WINDOW_MARGIN = 40
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun prepareLayoutForSnackbar(snackBarInAppType: InAppType.Snackbar) {
        var statusBarHeight = 0
        val statusBarResourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (statusBarResourceId > 0) {
            statusBarHeight = resources.getDimensionPixelSize(statusBarResourceId)
        }
        var navigationBarHeight = 0
        val navBarResourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (navBarResourceId > 0) {
            navigationBarHeight = resources.getDimensionPixelSize(navBarResourceId)
        }
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
        var rightDY = 0f
        var startingY = 0f
        var lastY = if (snackBarInAppType.isTop()) Float.MAX_VALUE else 0f
        setOnTouchListener { view, event ->
            when (event?.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    rightDY = view.y - event.rawY
                    startingY = view.y
                }

                MotionEvent.ACTION_MOVE -> {
                    if (snackBarInAppType.isTop() && lastY > event.rawY) {
                        val displacement = event.rawY + rightDY
                        view!!.animate()
                            .y(displacement)
                            .setDuration(0)
                            .start()
                    } else if (!snackBarInAppType.isTop() && lastY < event.rawY) {
                        val displacement = event.rawY + rightDY
                        view!!.animate()
                            .y(displacement)
                            .setDuration(0)
                            .start()
                    }
                    lastY = event.rawY
                }

                MotionEvent.ACTION_UP -> {
                    if (abs(view.translationY) > (height / 2)) {
                        swipeToDismissCallback?.invoke()
                    } else {
                        view.y = startingY
                    }
                }

                else -> { // Note the block
                    return@setOnTouchListener false
                }
            }
            true
        }
    }

    fun slideUp() {
        val animate = TranslateAnimation(
            0f,  // fromXDelta
            0f,  // toXDelta
            height.toFloat(),  // fromYDelta
            0f
        ) // toYDelta
        animate.duration = ANIM_DURATION
        animate.fillAfter = true
        animate.interpolator = LinearOutSlowInInterpolator()
        startAnimation(animate)
    }

    fun slideDown() {
        val animate = TranslateAnimation(
            0f,  // fromXDelta
            0f,  // toXDelta
            -height.toFloat(),  // fromYDelta
            0f
        ) // toYDelta
        animate.duration = ANIM_DURATION
        animate.fillAfter = true
        animate.interpolator = LinearOutSlowInInterpolator()
        startAnimation(animate)
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
            is InAppType.ModalWindow -> prepareLayoutForModalWindow()
            is InAppType.Snackbar -> prepareLayoutForSnackbar(inAppType)
        }
    }


    @RequiresApi(21)
    constructor(
        context: Context,
        attrs: AttributeSet,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(
        context, attrs, defStyleAttr, defStyleRes
    )

    override fun setDismissListener(listener: OnClickListener) {
        backButtonHandler = BackButtonHandler(this, listener)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_BACK)
            true else super.onKeyDown(keyCode, event)
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        val handled = backButtonHandler?.dispatchKeyEvent(event)
        return handled ?: super.dispatchKeyEvent(event)
    }
}