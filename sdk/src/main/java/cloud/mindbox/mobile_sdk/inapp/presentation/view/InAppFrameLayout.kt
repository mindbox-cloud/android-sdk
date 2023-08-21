package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import kotlin.math.abs

internal typealias SnackbarPosition = InAppType.Snackbar.Position.Gravity.VerticalGravity

internal class InAppFrameLayout : FrameLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    @SuppressLint("ClickableViewAccessibility")
    fun updateView(inAppType: InAppType) {
        val snackBarInAppType = inAppType as? InAppType.Snackbar ?: return
        updateLayoutParams<MarginLayoutParams> {
            setMargins(
                snackBarInAppType.position.margin.left,
                snackBarInAppType.position.margin.top,
                snackBarInAppType.position.margin.right,
                snackBarInAppType.position.margin.bottom
            )
        }
        when (inAppType.position.gravity.vertical) {
            SnackbarPosition.TOP -> {
                updateLayoutParams<MarginLayoutParams> {
                    setMargins(
                        inAppType.position.margin.left,
                        inAppType.position.margin.top,
                        inAppType.position.margin.right,
                        0
                    )
                }
            }

            SnackbarPosition.BOTTOM -> {
                updateLayoutParams<MarginLayoutParams> {
                    setMargins(
                        inAppType.position.margin.left,
                        0,
                        inAppType.position.margin.right,
                        inAppType.position.margin.bottom
                    )
                }
            }
        }
        var rightDY = 0f
        setOnTouchListener { view, event ->
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    rightDY = view!!.y - event.rawY
                }

                MotionEvent.ACTION_MOVE -> {
                    val displacement =
                        if (inAppType.position.gravity.vertical == SnackbarPosition.TOP) minOf(
                            0f,
                            event.rawY + rightDY
                        ) else maxOf(0f, event.rawY + rightDY)

                    view!!.animate()
                        .y(displacement)
                        .alpha(view.height / displacement)
                        .setDuration(0)
                        .start()

                }

                MotionEvent.ACTION_UP -> {
                    view!!.animate()
                        .y(0f)
                        .setDuration(100)
                        .start()
                }

                else -> { // Note the block
                    return@setOnTouchListener false
                }
            }
            true
        }
    }

    private fun slideUp(view: View, onCompleted: () -> Unit, isTop:Boolean) {
        view.visibility = View.VISIBLE
        val animate = TranslateAnimation(
            0f,  // fromXDelta
            0f,  // toXDelta
            if (isTop) -view.height.toFloat() else 0f,  // fromYDelta
            if (isTop) 0f else view.height.toFloat()
        ) // toYDelta
        animate.duration = duration
        animate.fillAfter = true
        animate.interpolator = LinearOutSlowInInterpolator()
        animate.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
            }

            override fun onAnimationEnd(animation: Animation?) {
                onCompleted()
            }

            override fun onAnimationRepeat(animation: Animation?) {
            }

        })
        view.startAnimation(animate)
    }

    val duration = 300L

    // slide the view from its current position to below itself
    private fun slideDown(view: View, onCompleted: () -> Unit, isTop: Boolean) {
        val animate = TranslateAnimation(
            0f,  // fromXDelta
            0f,  // toXDelta
            if (isTop) 0f else view.height.toFloat(),
            if (isTop) -view.height.toFloat() else 0f
        ) // toYDelta
        animate.duration = duration
        animate.fillAfter = true
        animate.interpolator = FastOutLinearInInterpolator()
        animate.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
            }

            override fun onAnimationEnd(animation: Animation?) {
                onCompleted()
            }

            override fun onAnimationRepeat(animation: Animation?) {
            }

        })
        view.startAnimation(animate)
    }
}