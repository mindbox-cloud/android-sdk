package cloud.mindbox.mobile_sdk.inapp

import android.view.View

interface BackButtonLayout {
    fun setDismissListener(listener: View.OnClickListener)
}