package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.view.KeyEvent
import cloud.mindbox.mobile_sdk.logger.mindboxLogI

internal class BackButtonHandler(
    private val listener: () -> Unit,
) {
    /**
     * Returns true if the event was consumed, null if it was not a back key event.
     */
    fun dispatchKeyEvent(event: KeyEvent?): Boolean? {
        if (event?.keyCode != KeyEvent.KEYCODE_BACK || event.action != KeyEvent.ACTION_UP || event.isCanceled) {
            return null
        }
        mindboxLogI("BackButtonHandler: KEYCODE_BACK ACTION_UP")
        listener()
        return true
    }
}
