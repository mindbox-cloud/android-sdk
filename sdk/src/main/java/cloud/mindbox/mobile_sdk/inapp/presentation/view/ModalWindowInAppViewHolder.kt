package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import cloud.mindbox.mobile_sdk.R
import cloud.mindbox.mobile_sdk.inapp.domain.models.Element
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTypeWrapper
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback
import cloud.mindbox.mobile_sdk.inapp.presentation.MindboxView
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.removeChildById

internal class ModalWindowInAppViewHolder(
    override val wrapper: InAppTypeWrapper<InAppType.ModalWindow>,
    private val inAppCallback: InAppCallback,
) : AbstractInAppViewHolder<InAppType.ModalWindow>() {

    private var currentBackground: ViewGroup? = null

    override val isActive: Boolean
        get() = isInAppMessageActive

    override fun bind() {
        inAppLayout.setDismissListener {
            inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
            mindboxLogI("In-app dismissed by dialog click")
            hide()
        }
        wrapper.inAppType.elements.forEach { element ->
            when (element) {
                is Element.CloseButton -> {
                    val inAppCrossView = InAppCrossView(
                        currentDialog.context,
                        element
                    ).apply {
                        setOnClickListener {
                            mindboxLogI("In-app dismissed by close click")
                            inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
                            hide()
                        }
                    }
                    inAppLayout.addView(inAppCrossView)
                    inAppCrossView.prepareViewForModalWindow(inAppLayout)
                }
            }
        }
        currentBackground?.apply {
            setOnClickListener {
                inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
                mindboxLogI("In-app dismissed by background click")
                hide()
            }

            isVisible = true
        }
    }

    override fun addUrlSource(layer: Layer.ImageLayer, inAppCallback: InAppCallback) {
        super.addUrlSource(layer, inAppCallback)
        when (layer.source) {
            is Layer.ImageLayer.Source.UrlSource -> {
                InAppImageView(currentDialog.context).also { inAppImageView ->
                    inAppImageView.visibility = View.INVISIBLE
                    inAppLayout.addView(inAppImageView)
                    inAppImageView.prepareViewForModalWindow(inAppLayout)
                    preparedImages[inAppImageView] = false
                    getImageFromCache(layer.source.url, inAppImageView)
                }
            }
        }
    }

    override fun show(currentRoot: MindboxView) {
        super.show(currentRoot)
        mindboxLogI("Try to show in-app with id ${wrapper.inAppType.inAppId}")
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
        currentDialog.removeChildById(R.id.inapp_background_layout)
        currentBackground = LayoutInflater.from(currentRoot.context)
            .inflate(R.layout.mindbox_blur_layout, currentRoot, false) as FrameLayout
        currentDialog.addView(currentBackground, 0)
    }
}
