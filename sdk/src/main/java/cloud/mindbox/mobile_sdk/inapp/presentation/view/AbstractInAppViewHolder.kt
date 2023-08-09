package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import cloud.mindbox.mobile_sdk.R
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.logger.mindboxLogI

internal abstract class AbstractInAppViewHolder<T : InAppType> : InAppViewHolder<InAppType> {


    private var _currentBackground: View? = null
    protected val currentBackground: View
        get() = _currentBackground!!

    private var _currentDialog: InAppConstraintLayout? = null
    protected val currentDialog: InAppConstraintLayout
        get() = _currentDialog!!

    private var typingView: View? = null

    private fun hideKeyboard(currentRoot: ViewGroup) {
        val context = currentRoot.context
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        if (imm?.isAcceptingText == true) {
            typingView = currentRoot.findFocus()
            imm.hideSoftInputFromWindow(
                currentRoot.windowToken,
                0
            )
        }
    }

    protected fun initView(currentRoot: ViewGroup) {
        _currentBackground = LayoutInflater.from(currentRoot.context).inflate(R.layout.mindbox_blur_layout, currentRoot, false)
        _currentDialog = LayoutInflater.from(currentRoot.context).inflate(R.layout.mindbox_default_inapp_layout, currentRoot, false) as InAppConstraintLayout
        currentRoot.addView(currentBackground)
        currentRoot.addView(currentDialog)
    }

    private fun restoreKeyboard() {
        typingView?.let { view ->
            view.requestFocus()
            val imm =
                (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?)
            imm?.showSoftInput(
                view,
                InputMethodManager.SHOW_IMPLICIT
            )
        }
    }

    override fun show(currentRoot: ViewGroup) {
        initView(currentRoot)
        hideKeyboard(currentRoot)
    }

    override fun hide() {
        mindboxLogI("hide ${wrapper.inAppType.inAppId} on ${this.hashCode()}")
        restoreKeyboard()
    }
}