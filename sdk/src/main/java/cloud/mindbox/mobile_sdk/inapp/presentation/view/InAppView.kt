package cloud.mindbox.mobile_sdk.inapp.presentation.view

import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType

internal interface InAppView {
    fun updateView(inApp: InAppType, currentDialog: InAppConstraintLayout)
}