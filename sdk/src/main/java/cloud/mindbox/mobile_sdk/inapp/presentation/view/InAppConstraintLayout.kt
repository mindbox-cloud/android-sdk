package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.*
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.px
import kotlin.math.abs


internal typealias SnackbarPosition = InAppType.Snackbar.Position.Gravity.VerticalGravity


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
    }

    init {
        visibility = View.INVISIBLE
    }


    @SuppressLint("ClickableViewAccessibility")
    private fun prepareLayoutForSnackbar(snackBarInAppType: InAppType.Snackbar) {
        maxHeight = resources.displayMetrics.heightPixels / 3
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            when (snackBarInAppType.position.gravity.vertical) {
                SnackbarPosition.TOP -> {
                    updateLayoutParams<FrameLayout.LayoutParams> {
                        gravity = Gravity.TOP
                        setMargins(
                            snackBarInAppType.position.margin.left,
                            snackBarInAppType.position.margin.top + insets.top,
                            snackBarInAppType.position.margin.right,
                            0
                        )
                    }
                }

                SnackbarPosition.BOTTOM -> {
                    updateLayoutParams<FrameLayout.LayoutParams> {
                        gravity = Gravity.BOTTOM
                        setMargins(
                            snackBarInAppType.position.margin.left,
                            0,
                            snackBarInAppType.position.margin.right,
                            snackBarInAppType.position.margin.bottom + insets.bottom
                        )
                    }
                }
            }
            WindowInsetsCompat.CONSUMED
        }
        var rightDY = 0f
        var startingY = 0f
        setOnTouchListener { view, event ->
            when (event?.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    rightDY = view.y - event.rawY
                    startingY = view.y
                }

                MotionEvent.ACTION_MOVE -> {
                    val displacement = event.rawY + rightDY
                    view!!.animate()
                        .y(displacement)
                        .setDuration(0)
                        .start()
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
                40.px, 40.px, 40.px, 40.px
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