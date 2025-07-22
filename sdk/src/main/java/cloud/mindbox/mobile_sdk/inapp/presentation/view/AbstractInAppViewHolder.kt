package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import cloud.mindbox.mobile_sdk.R
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageViewDisplayerImpl
import cloud.mindbox.mobile_sdk.inapp.presentation.MindboxView
import cloud.mindbox.mobile_sdk.inapp.presentation.actions.InAppActionHandler
import cloud.mindbox.mobile_sdk.logger.mindboxLogE
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.removeChildById
import cloud.mindbox.mobile_sdk.setSingleClickListener
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

internal abstract class AbstractInAppViewHolder<T : InAppType> : InAppViewHolder<InAppType> {

    protected open var isInAppMessageActive = false

    private var positionController: InAppPositionController? = null

    private var _currentDialog: InAppConstraintLayout? = null
    protected val currentDialog: InAppConstraintLayout
        get() = _currentDialog!!

    private var typingView: View? = null

    protected val preparedImages: MutableMap<ImageView, Boolean> = mutableMapOf()

    private val mindboxNotificationManager by mindboxInject {
        mindboxNotificationManager
    }

    private var inAppActionHandler = InAppActionHandler()

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
        if (InAppMessageViewDisplayerImpl.isActionExecuted) return
        var redirectUrl: String
        var payload: String
        var shouldDismiss: Boolean

        currentDialog.setSingleClickListener {
            val inAppData = inAppActionHandler.handle(
                layer.action,
                inAppActionHandler.mindboxView
            )

            with(inAppData) {
                redirectUrl = this.redirectUrl
                payload = this.payload
                shouldDismiss = this.shouldDismiss
            }

            wrapper.onInAppClick.onClick()
            inAppCallback.onInAppClick(
                wrapper.inAppType.inAppId,
                redirectUrl,
                payload
            )

            if (shouldDismiss) {
                inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
                mindboxLogI("In-app dismissed by click")
                hide()
            }

            inAppData.onCompleted?.invoke()

            InAppMessageViewDisplayerImpl.isActionExecuted = true
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
                    return runCatching {
                        this.mindboxLogE(
                            message = "Failed to load inapp image with url = $url",
                            exception = e
                                ?: RuntimeException("Failed to load inapp image with url = $url")
                        )
                        hide()
                        false
                    }.getOrElse {
                        mindboxLogE(
                            "Unknown error when loading image from cache succeeded",
                            exception = it
                        )
                        false
                    }
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    return runCatching {
                        bind()
                        preparedImages[imageView] = true
                        if (!preparedImages.values.contains(false)) {
                            mindboxLogI("In-app shown")
                            wrapper.onInAppShown.onShown()
                            for (image in preparedImages.keys) {
                                image.visibility = View.VISIBLE
                            }
                        }
                        false
                    }.getOrElse {
                        mindboxLogE(
                            "Unknown error when loading image from cache failed",
                            exception = it
                        )
                        false
                    }
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

    override fun show(currentRoot: MindboxView) {
        initView(currentRoot.container)
        val isRepositioningEnabled = currentRoot.container.context.resources.getBoolean(R.bool.mindbox_support_inapp_on_fragment)
        positionController = isRepositioningEnabled.takeIf { it }?.run {
            InAppPositionController().apply { start(currentDialog) }
        }
        hideKeyboard(currentRoot.container)
        inAppActionHandler.mindboxView = currentRoot
    }

    override fun hide() {
        positionController?.stop()
        positionController = null
        (currentDialog.parent as? ViewGroup?)?.apply {
            removeView(_currentDialog)
        }
        mindboxLogI("hide ${wrapper.inAppType.inAppId} on ${this.hashCode()}")
        restoreKeyboard()
    }
}
