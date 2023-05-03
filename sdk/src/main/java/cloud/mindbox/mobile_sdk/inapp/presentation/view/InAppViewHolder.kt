package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.view.ViewGroup
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTypeWrapper

internal interface InAppViewHolder<T : InAppType> {

    val wrapper: InAppTypeWrapper<T>

    val isActive: Boolean

    fun show(currentRoot: ViewGroup)

    fun hide()

}