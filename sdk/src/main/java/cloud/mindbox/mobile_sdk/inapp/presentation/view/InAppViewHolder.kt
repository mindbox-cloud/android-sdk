package cloud.mindbox.mobile_sdk.inapp.presentation.view

import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTypeWrapper
import cloud.mindbox.mobile_sdk.inapp.presentation.MindboxView

internal interface InAppViewHolder<T : InAppType> {

    val wrapper: InAppTypeWrapper<T>

    val isActive: Boolean

    fun show(currentRoot: MindboxView)

    fun hide()

}