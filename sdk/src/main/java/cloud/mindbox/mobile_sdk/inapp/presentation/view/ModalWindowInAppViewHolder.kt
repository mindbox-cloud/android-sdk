package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import cloud.mindbox.mobile_sdk.R
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

    private lateinit var currentBlur: View
    private lateinit var currentDialog: InAppConstraintLayout

    private val shouldUseBlur = true

    private var isInAppMessageActive = false

    override val isActive: Boolean
        get() = isInAppMessageActive


    private fun bind(currentRoot: ViewGroup) {
        /* currentRoot.findViewById<ImageView>(R.id.iv_close)?.apply {
             isVisible = true
             setOnClickListener {
                 mindboxLogI("In-app dismissed by close click")
                 inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
                 hide()
                 isInAppMessageActive = false
             }
         }*/

        currentRoot.addView(CrossView(currentRoot.context).apply {
            setOnClickListener {
                mindboxLogI("In-app dismissed by close click")
                inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
                hide()
                isInAppMessageActive = false
            }
        })

        currentDialog.setSingleClickListener {
            wrapper.onInAppClick.onClick()
            inAppCallback.onInAppClick(
                wrapper.inAppType.inAppId,
                wrapper.inAppType.redirectUrl,
                wrapper.inAppType.intentData
            )
            if (wrapper.inAppType.redirectUrl.isNotBlank() || wrapper.inAppType.intentData.isNotBlank()) {
                inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
                mindboxLogI("In-app dismissed by click")
                isInAppMessageActive = false
                hide()
            }
        }
        currentDialog.setDismissListener {
            inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
            mindboxLogI("In-app dismissed by dialog click")
            isInAppMessageActive = false
            hide()
        }
        currentBlur.setOnClickListener {
            inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
            mindboxLogI("In-app dismissed by background click")
            isInAppMessageActive = false
            hide()
        }
        currentBlur.isVisible = true
        mindboxLogI("In-app shown")
        wrapper.onInAppShown.onShown()
    }

    override fun show(currentRoot: ViewGroup) {
        super.show(currentRoot)
        mindboxLogI("Show ${wrapper.inAppType.inAppId} on ${this.hashCode()}")
        if (wrapper.inAppType.imageUrl.isBlank()) {
            mindboxLogI("In-app image url is blank")
            return
        }
        isInAppMessageActive = true

        currentRoot.addView(currentBlur)
        currentRoot.addView(currentDialog)
        currentDialog.requestFocus()

        with(currentRoot.findViewById<ImageView>(R.id.iv_content)) {
            mindboxLogI("Try to show inapp with id ${wrapper.inAppType.inAppId}")
            Glide
                .with(currentRoot.context.applicationContext)
                .load(wrapper.inAppType.imageUrl)
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
                            exception = e ?: RuntimeException("Failed to load inapp image")
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

    override fun hide() {
        super.hide()
        (currentDialog.parent as? ViewGroup?)?.apply {
            removeView(currentDialog)
            removeView(currentBlur)
        }
    }
}