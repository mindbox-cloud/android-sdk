package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.px
internal typealias SnackbarPosition = InAppType.Snackbar.Position.Gravity.VerticalGravity


internal class InAppConstraintLayout : ConstraintLayout, BackButtonLayout {

    private var backButtonHandler: BackButtonHandler? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )
    @SuppressLint("ClickableViewAccessibility")
    private fun prepareLayoutForSnackbar(snackBarInAppType: InAppType.Snackbar) {
        updateLayoutParams<MarginLayoutParams> {
            maxHeight = resources.displayMetrics.heightPixels / 3
            setMargins(
                snackBarInAppType.position.margin.left,
                snackBarInAppType.position.margin.top,
                snackBarInAppType.position.margin.right,
                snackBarInAppType.position.margin.bottom
            )
        }
        when (snackBarInAppType.position.gravity.vertical) {
            SnackbarPosition.TOP -> {
                updateLayoutParams<FrameLayout.LayoutParams> {
                    gravity = Gravity.TOP
                    setMargins(
                        snackBarInAppType.position.margin.left,
                        snackBarInAppType.position.margin.top,
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
                        snackBarInAppType.position.margin.bottom
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
                        if (snackBarInAppType.position.gravity.vertical == SnackbarPosition.TOP) minOf(
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

    private fun prepareLayoutForModalWindow(modalWindowInAppType: InAppType.ModalWindow) {
        updateLayoutParams<MarginLayoutParams> {
            setMargins(
                40.px, 40.px, 40.px, 40.px
            )
        }
        updateLayoutParams<FrameLayout.LayoutParams> {
            gravity = Gravity.CENTER
        }
    }

    fun updateView(inAppType: InAppType) {
        when (inAppType) {
            is InAppType.ModalWindow -> prepareLayoutForModalWindow(inAppType)
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