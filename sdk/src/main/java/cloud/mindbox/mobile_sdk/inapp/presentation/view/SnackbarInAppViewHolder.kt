package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.graphics.Point
import android.graphics.drawable.Drawable
import android.view.Display
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.isVisible
import cloud.mindbox.mobile_sdk.inapp.domain.models.Element
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTypeWrapper
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.setSingleClickListener
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target


internal class SnackbarInAppViewHolder(override val wrapper: InAppTypeWrapper<InAppType.Snackbar>, private val inAppCallback: InAppCallback) :
    AbstractInAppViewHolder<InAppType.Snackbar, InAppFrameLayout>() {

    private var isInAppMessageActive = false

    override val isActive: Boolean
        get() = isInAppMessageActive

    override fun initView(currentRoot: ViewGroup) {
        super.initView(currentRoot)
        initDialog(currentRoot, InAppType.Snackbar::class.java)
        currentRoot.addView(currentBackground)
        currentRoot.addView(currentDialog)
        currentDialog.updateView(wrapper.inAppType)
    }

    override fun show(currentRoot: ViewGroup) {
        super.show(currentRoot)
        wrapper.inAppType.layers.forEach { layer ->
            when (layer) {
                is Layer.ImageLayer -> {
                    addUrlSource(currentRoot, layer)
                }
            }
        }
        mindboxLogI("Show ${wrapper.inAppType.inAppId} on ${this.hashCode()}")
        isInAppMessageActive = true
        currentDialog.requestFocus()
    }

    private fun addUrlSource(currentRoot: ViewGroup, layer: Layer.ImageLayer) {
        InAppImageView(currentRoot.context).apply {
            currentDialog.setSingleClickListener {
                var redirectUrl = ""
                var payload = ""
                when (layer.action) {
                    is Layer.ImageLayer.Action.RedirectUrlAction -> {
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
                is Layer.ImageLayer.Source.UrlSource -> {
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
                                    this.mindboxLogE(
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
                                    bind()
                                    return false
                                }
                            })
                            .centerCrop()
                            .into(this)
                    }
                }
            }
            currentDialog.addView(this)
            updateView(currentDialog)
        }
    }


    private fun bind() {
        wrapper.inAppType.elements.forEach { element ->
            when (element) {
                is Element.CloseButton -> {
                    val inAppCrossView = InAppCrossView(currentDialog.context, element).apply {
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
        currentBackground.isVisible = true
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