package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.logger.mindboxLogI

internal abstract class AbstractInAppViewHolder<T : InAppType> : InAppViewHolder<InAppType> {


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
        hideKeyboard(currentRoot)
    }

    override fun hide() {
        mindboxLogI("hide ${wrapper.inAppType.inAppId} on ${this.hashCode()}")
        restoreKeyboard()
    }
}