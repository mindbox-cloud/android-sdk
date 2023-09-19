package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import cloud.mindbox.mobile_sdk.R
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.removeChildById
import cloud.mindbox.mobile_sdk.setSingleClickListener
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target


internal abstract class AbstractInAppViewHolder<T : InAppType> :
    InAppViewHolder<InAppType> {

    protected open var isInAppMessageActive = false

    private var _currentDialog: InAppConstraintLayout? = null
    protected val currentDialog: InAppConstraintLayout
        get() = _currentDialog!!

    private var typingView: View? = null

    private fun hideKeyboard(currentRoot: ViewGroup) {
        val context = currentRoot.context
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        if (imm?.isAcceptingText == true) {
            typingView = currentRoot.findFocus()
            imm.hideSoftInputFromWindow(
                currentRoot.windowToken,
                0
            )
        }
    }

    abstract fun bind()

    protected open fun addUrlSource(layer: Layer.ImageLayer, inAppCallback: InAppCallback) {
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
                hide()
            }
        }
    }

    protected fun getImageFromCache(url: String, imageView: InAppImageView) {
        Glide
            .with(currentDialog.context)
            .load(url)
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
            .into(imageView)
    }

    protected open fun initView(currentRoot: ViewGroup) {
        currentRoot.removeChildById(R.id.inapp_layout)
        _currentDialog = LayoutInflater.from(currentRoot.context)
            .inflate(R.layout.mindbox_inapp_layout, currentRoot, false) as InAppConstraintLayout
        currentRoot.addView(currentDialog)
        currentDialog.prepareLayoutForInApp(wrapper.inAppType)
    }

    private fun restoreKeyboard() {
        typingView?.let { view ->
            view.requestFocus()
            val imm =
                (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?)
            imm?.showSoftInput(
                view,
                InputMethodManager.SHOW_IMPLICIT
            )
        }
    }

    override fun show(currentRoot: ViewGroup) {
        isInAppMessageActive = true
        initView(currentRoot)
        hideKeyboard(currentRoot)
    }

    override fun hide() {
        (currentDialog.parent as? ViewGroup?)?.apply {
            removeView(currentDialog)
        }
        mindboxLogI("hide ${wrapper.inAppType.inAppId} on ${this.hashCode()}")
        restoreKeyboard()
    }
}