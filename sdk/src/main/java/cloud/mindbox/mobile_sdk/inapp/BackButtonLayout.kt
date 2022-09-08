package cloud.mindbox.mobile_sdk.inapp

import android.view.View

internal interface BackButtonLayout {
    fun setDismissListener(listener: View.OnClickListener)
}