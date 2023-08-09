package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.graphics.drawable.Drawable
import android.view.ViewGroup
import androidx.core.view.isVisible
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTypeWrapper
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.setSingleClickListener
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


    private var isInAppMessageActive = false

    override val isActive: Boolean
        get() = isInAppMessageActive


    private fun bind(currentRoot: ViewGroup) {
        currentDialog.setDismissListener {
            inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
            mindboxLogI("In-app dismissed by dialog click")
            isInAppMessageActive = false
            hide()
        }
        wrapper.inAppType.elements.forEach { element ->
            when (element) {
                is InAppType.ModalWindow.Element.CloseButton -> {
                    val inAppCrossView = InAppCrossView(currentRoot.context, element).apply {
                        setOnClickListener {
                            mindboxLogI("In-app dismissed by close click")
                            inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
                            hide()
                            isInAppMessageActive = false
                        }
                    }
                    currentDialog.addView(inAppCrossView)
                    inAppCrossView.updateView(currentDialog)
                }
            }
        }
        currentBackground.setOnClickListener {
            inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
            mindboxLogI("In-app dismissed by background click")
            isInAppMessageActive = false
            hide()
        }
        currentBackground.isVisible = true
        mindboxLogI("In-app shown")
        wrapper.onInAppShown.onShown()
    }

    override fun show(currentRoot: ViewGroup) {
        super.show(currentRoot)
        wrapper.inAppType.layers.forEach { layer ->
            when (layer) {
                is InAppType.ModalWindow.Layer.ImageLayer -> {
                    with(InAppImageView(currentRoot.context).apply {
                        currentDialog.setSingleClickListener {
                            var redirectUrl = ""
                            var payload = ""
                            when (layer.action) {
                                is InAppType.ModalWindow.Layer.ImageLayer.Action.RedirectUrlAction -> {
                                    redirectUrl = layer.action.url
                                    payload = layer.action.payload
                                }
                            }
                            wrapper.onInAppClick.onClick()
                            inAppCallback.onInAppClick(
                                wrapper.inAppType.inAppId,
                                redirectUrl,
                                payload
                            )
                            if (redirectUrl.isNotBlank() || payload.isNotBlank()) {
                                inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
                                mindboxLogI("In-app dismissed by click")
                                isInAppMessageActive = false
                                hide()
                            }
                        }
                        when (layer.source) {
                            is InAppType.ModalWindow.Layer.ImageLayer.Source.UrlSource -> {
                                with(this) {
                                    mindboxLogI("Try to show inapp with id ${wrapper.inAppType.inAppId}")
                                    Glide
                                        .with(currentRoot.context.applicationContext)
                                        .load(layer.source.url)
                                        .onlyRetrieveFromCache(true)
                                        .listener(object : RequestListener<Drawable> {
                                            override fun onLoadFailed(
                                                e: GlideException?,
                                                model: Any?,
                                                target: Target<Drawable>?,
                                                isFirstResource: Boolean
                                            ): Boolean {
                                                this@ModalWindowInAppViewHolder.mindboxLogE(
                                                    message = "Failed to load inapp image",
                                                    exception = e
                                                        ?: RuntimeException("Failed to load inapp image")
                                                )
                                                hide()
                                                isInAppMessageActive = false
                                                return false
                                            }

                                            override fun onResourceReady(
                                                resource: Drawable?,
                                                model: Any?,
                                                target: Target<Drawable>?,
                                                dataSource: DataSource?,
                                                isFirstResource: Boolean
                                            ): Boolean {
                                                bind(currentRoot)
                                                return false
                                            }
                                        })
                                        .centerCrop()
                                        .into(this)
                                }
                            }
                        }
                    }) {
                        currentDialog.addView(this)
                        updateView(currentDialog)
                    }

                }
            }
        }
        mindboxLogI("Show ${wrapper.inAppType.inAppId} on ${this.hashCode()}")
        isInAppMessageActive = true
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