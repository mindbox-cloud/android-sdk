package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import cloud.mindbox.mobile_sdk.inapp.presentation.view.BackButtonHandler
import cloud.mindbox.mobile_sdk.inapp.presentation.view.BackButtonLayout

internal class InAppConstraintLayout : ConstraintLayout, BackButtonLayout {

    private var backButtonHandler: BackButtonHandler? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr) {
    }

    @RequiresApi(21)
    constructor(
        context: Context,
        attrs: AttributeSet,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(
        context, attrs, defStyleAttr, defStyleRes)

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