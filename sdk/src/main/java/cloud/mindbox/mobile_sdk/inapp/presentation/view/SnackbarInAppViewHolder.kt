package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.view.ViewGroup
import cloud.mindbox.mobile_sdk.inapp.domain.models.Element
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTypeWrapper
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback
import cloud.mindbox.mobile_sdk.logger.mindboxLogI


internal class SnackbarInAppViewHolder(
    override val wrapper: InAppTypeWrapper<InAppType.Snackbar>,
    private val inAppCallback: InAppCallback
) :
    AbstractInAppViewHolder<InAppType.Snackbar>() {

    override val isActive: Boolean
        get() = isInAppMessageActive

    override fun show(currentRoot: ViewGroup) {
        super.show(currentRoot)
        wrapper.inAppType.layers.forEach { layer ->
            when (layer) {
                is Layer.ImageLayer -> {
                    addUrlSource(layer, inAppCallback)
                }
            }
        }
        mindboxLogI("Show ${wrapper.inAppType.inAppId} on ${this.hashCode()}")
        currentDialog.requestFocus()
    }

    override fun initView(currentRoot: ViewGroup) {
        super.initView(currentRoot)
        currentDialog.setSwipeToDismissCallback {
            hide()
        }
    }

    override fun bind() {
        wrapper.inAppType.elements.forEach { element ->
            when (element) {
                is Element.CloseButton -> {
                    val inAppCrossView = InAppCrossView(currentDialog.context, element).apply {
                        setOnClickListener {
                            mindboxLogI("In-app dismissed by close click")
                            inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
                            hide()
                        }
                    }
                    currentDialog.addView(inAppCrossView)
                    inAppCrossView.setInAppParams(wrapper.inAppType, currentDialog)
                }
            }
        }
        when (wrapper.inAppType.position.gravity.vertical) {
            SnackbarPosition.TOP -> {
                currentDialog.slideDown()
            }

            SnackbarPosition.BOTTOM -> {
                currentDialog.slideUp()
            }
        }

        mindboxLogI("In-app shown")
        wrapper.onInAppShown.onShown()
    }

    override fun hide() {
        super.hide()
        (currentDialog.parent as? ViewGroup?)?.apply {
            removeView(currentDialog)
            removeView(currentBackground)
        }
    }
}