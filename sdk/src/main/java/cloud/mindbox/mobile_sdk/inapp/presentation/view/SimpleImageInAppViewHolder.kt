package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import cloud.mindbox.mobile_sdk.R
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTypeWrapper
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener


internal class SimpleImageInAppViewHolder(
    override val wrapper: InAppTypeWrapper<InAppType.SimpleImage>,
    private val inAppCallback: InAppCallback
) : InAppViewHolder<InAppType.SimpleImage> {

    private lateinit var currentBlur: View
    private lateinit var currentDialog: InAppConstraintLayout

    private val shouldUseBlur = true

    private var isInAppMessageActive = false

    override val isActive: Boolean
        get() = isInAppMessageActive

    private fun initView(currentRoot: ViewGroup) {
        val context = currentRoot.context
        val inflater = LayoutInflater.from(context)

        currentBlur = inflater.inflate(
            R.layout.blur_layout,
            currentRoot,
            false
        )

        if (!shouldUseBlur) {
            mindboxLogD("Disable blur")
            currentBlur.setBackgroundColor(
                ContextCompat.getColor(context, android.R.color.transparent)
            )
        }

        currentDialog = inflater.inflate(
            R.layout.default_inapp_layout,
            currentRoot, false
        ) as InAppConstraintLayout
    }

    private fun bind(currentRoot: ViewGroup) {
        currentRoot.findViewById<ImageView>(R.id.iv_close)?.apply {
            isVisible = true
            setOnClickListener {
                mindboxLogD("In-app dismissed")
                inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
                hide()
                isInAppMessageActive = false
            }
        }

        currentDialog.setOnClickListener {
            currentDialog.isEnabled = false
            wrapper.onInAppClick.onClick()
            inAppCallback.onInAppClick(
                wrapper.inAppType.inAppId,
                wrapper.inAppType.redirectUrl,
                wrapper.inAppType.intentData
            )
            if (wrapper.inAppType.redirectUrl.isNotBlank() || wrapper.inAppType.intentData.isNotBlank()) {
                currentRoot.removeView(currentDialog)
                currentRoot.removeView(currentBlur)
                isInAppMessageActive = false
            }
        }
        currentDialog.setDismissListener {
            inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
            mindboxLogD("In-app dismissed")

            isInAppMessageActive = false
            hide()
        }
        currentBlur.setOnClickListener {
            inAppCallback.onInAppDismissed(wrapper.inAppType.inAppId)
            mindboxLogD("In-app dismissed")
            isInAppMessageActive = false
            hide()
        }
        currentBlur.isVisible = true
        mindboxLogD("inapp shown")
        wrapper.onInAppShown.onShown()
    }

    override fun show(currentRoot: ViewGroup) {
        mindboxLogD("show ${wrapper.inAppType.inAppId} on ${this.hashCode()}")
        if (wrapper.inAppType.imageUrl.isBlank()) {
            mindboxLogD("in-app image url is blank")
            return
        }

        initView(currentRoot)
        isInAppMessageActive = true

        currentRoot.addView(currentBlur)
        currentRoot.addView(currentDialog)
        currentDialog.requestFocus()

        with(currentRoot.findViewById<ImageView>(R.id.iv_content)) {
            mindboxLogD("try to show inapp with id ${wrapper.inAppType.inAppId}")
            Glide
                .with(currentRoot.context.applicationContext)
                .load(wrapper.inAppType.imageUrl)
                .onlyRetrieveFromCache(true)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<Drawable>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        MindboxLoggerImpl.e(
                            parent = this@SimpleImageInAppViewHolder,
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
                        target: com.bumptech.glide.request.target.Target<Drawable>?,
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
        mindboxLogD("hide ${wrapper.inAppType.inAppId} on ${this.hashCode()}")
        (currentDialog.parent as? ViewGroup?)?.apply {
            removeView(currentDialog)
            removeView(currentBlur)
        }
    }

}