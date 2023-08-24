package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.graphics.drawable.Drawable
import android.view.ViewGroup
import androidx.core.view.isVisible
import cloud.mindbox.mobile_sdk.inapp.domain.models.Element
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTypeWrapper
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

internal class ModalWindowInAppViewHolder(
    override val wrapper: InAppTypeWrapper<InAppType.ModalWindow>,
    private val inAppCallback: InAppCallback,
) :
    AbstractInAppViewHolder<InAppType.ModalWindow>() {

    override val isActive: Boolean
        get() = isInAppMessageActive


    override fun bind() {
        currentDialog.setDismissListener {
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
                    currentDialog.addView(inAppCrossView)
                    inAppCrossView.prepareViewForModalWindow(currentDialog)
                }
            }
        }
        currentBackground.setOnClickListener {
            inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
            mindboxLogI("In-app dismissed by background click")
            hide()
        }
        currentBackground.isVisible = true
        mindboxLogI("In-app shown")
        wrapper.onInAppShown.onShown()
    }

    override fun addUrlSource(layer: Layer.ImageLayer, inAppCallback: InAppCallback) {
        super.addUrlSource(layer, inAppCallback)
        when (layer.source) {
            is Layer.ImageLayer.Source.UrlSource -> {
                InAppImageView(currentDialog.context).also { inAppImageView ->
                    mindboxLogI("Try to show inapp with id ${wrapper.inAppType.inAppId}")
                    currentDialog.addView(inAppImageView)
                    inAppImageView.prepareViewForModalWindow(currentDialog)
                    getImageFromCache(layer.source.url, inAppImageView)
                }
            }
        }
    }

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

    override fun hide() {
        super.hide()
        (currentDialog.parent as? ViewGroup?)?.apply {
            removeView(currentDialog)
            removeView(currentBackground)
        }
    }
}