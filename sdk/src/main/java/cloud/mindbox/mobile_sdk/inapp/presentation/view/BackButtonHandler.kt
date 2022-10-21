package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup



internal class BackButtonHandler(
    private val viewGroup: ViewGroup,
    private val listener: View.OnClickListener?,
) {
    /** Returning "true" or "false" if the event was handled, "null" otherwise.  */
    fun dispatchKeyEvent(event: KeyEvent?): Boolean? {
        if (event != null && event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            if (listener != null) {
                listener.onClick(viewGroup)
                return true
            }
            return false
        }
        return null
    }
}