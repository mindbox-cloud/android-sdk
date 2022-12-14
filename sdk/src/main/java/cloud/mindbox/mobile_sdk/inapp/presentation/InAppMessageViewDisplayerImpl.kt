package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import cloud.mindbox.mobile_sdk.R
import cloud.mindbox.mobile_sdk.inapp.domain.InAppMessageViewDisplayer
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.presentation.view.InAppConstraintLayout
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import com.squareup.picasso.Callback
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso


internal class InAppMessageViewDisplayerImpl : InAppMessageViewDisplayer {

    private var currentRoot: ViewGroup? = null
    private var currentBlur: View? = null
    private var currentDialog: InAppConstraintLayout? = null
    private var currentActivity: Activity? = null
    private var inAppCallback: InAppCallback? = null


    override fun onResumeCurrentActivity(activity: Activity, shouldUseBlur: Boolean) {
        MindboxLoggerImpl.d(this, "onResumeCurrentActivity: ${activity.hashCode()}")
        currentRoot = activity.window.decorView.rootView as ViewGroup
        currentBlur = if (shouldUseBlur) {
            MindboxLoggerImpl.i(InAppMessageViewDisplayerImpl, "Enable blur")
            LayoutInflater.from(activity).inflate(R.layout.blur_layout,
                currentRoot, false
            )
        } else {
            MindboxLoggerImpl.i(InAppMessageViewDisplayerImpl, "Disable blur")
            LayoutInflater.from(activity).inflate(R.layout.blur_layout,
                currentRoot, false
            ).apply {
                setBackgroundColor(ContextCompat.getColor(activity,
                    android.R.color.transparent))
            }
        }
        currentActivity = activity
        currentDialog = LayoutInflater.from(activity).inflate(R.layout.default_inapp_layout,
            currentRoot, false
        ) as InAppConstraintLayout
    }

    override fun registerCurrentActivity(activity: Activity, shouldUseBlur: Boolean) {
        MindboxLoggerImpl.d(this, "registerCurrentActivity: ${activity.hashCode()}")
        currentRoot = activity.window.decorView.rootView as ViewGroup
        currentBlur = if (shouldUseBlur) {
            MindboxLoggerImpl.i(InAppMessageViewDisplayerImpl, "Enable blur")
            LayoutInflater.from(activity).inflate(R.layout.blur_layout,
                currentRoot, false
            )
        } else {
            MindboxLoggerImpl.i(InAppMessageViewDisplayerImpl, "Disable blur")
            LayoutInflater.from(activity).inflate(R.layout.blur_layout,
                currentRoot, false
            ).apply {
                setBackgroundColor(ContextCompat.getColor(activity,
                    android.R.color.transparent))
            }
        }
        currentActivity = activity
        currentDialog = LayoutInflater.from(activity).inflate(R.layout.default_inapp_layout,
            currentRoot, false
        ) as InAppConstraintLayout
    }


    override fun registerInAppCallback(inAppCallback: InAppCallback) {
        this.inAppCallback = inAppCallback
    }


    override fun onPauseCurrentActivity(activity: Activity) {
        MindboxLoggerImpl.d(this, "onPauseCurrentActivity: ${activity.hashCode()}")
        if (currentActivity == activity) {
            currentActivity = null
        }
        hideInAppMessage()
    }

    private fun hideInAppMessage() {
        isInAppMessageActive = false
        currentRoot?.removeView(currentBlur)
        currentRoot?.removeView(currentDialog)
        currentRoot = null
        currentDialog = null
        currentBlur = null
    }

    override suspend fun showInAppMessage(
        inAppType: InAppType,
        onInAppClick: () -> Unit,
        onInAppShown: () -> Unit,
    ) {
        when (inAppType) {
            is InAppType.SimpleImage -> {
                if (inAppType.imageUrl.isNotBlank()) {
                    if (currentRoot == null) {
                        MindboxLoggerImpl.e(this, "failed to show inapp: currentRoot is null")
                    }
                    currentRoot?.addView(currentBlur)
                    currentRoot?.addView(currentDialog)
                    currentDialog?.requestFocus()
                    with(currentRoot?.findViewById<ImageView>(R.id.iv_content)) {
                        MindboxLoggerImpl.d(this@InAppMessageViewDisplayerImpl,
                            "try to show inapp with id ${inAppType.inAppId}")
                        Picasso.get()
                            .load(inAppType.imageUrl)
                            .memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE)
                            .networkPolicy(NetworkPolicy.NO_STORE, NetworkPolicy.NO_CACHE)
                            .fit()
                            .centerCrop()
                            .into(this, object : Callback {
                                override fun onSuccess() {
                                    currentRoot?.findViewById<ImageView>(R.id.iv_close)?.apply {
                                        setOnClickListener {
                                            inAppCallback?.onInAppDismissed(inAppType.inAppId)
                                            currentRoot?.removeView(currentDialog)
                                            currentRoot?.removeView(currentBlur)
                                        }
                                        currentDialog?.setOnClickListener {
                                            currentDialog?.isEnabled = false
                                            onInAppClick()
                                            inAppCallback?.onInAppClick(inAppType.inAppId,
                                                inAppType.redirectUrl,
                                                inAppType.intentData)
                                            if (inAppType.redirectUrl.isNotBlank() || inAppType.intentData.isNotBlank()) {
                                                currentRoot?.removeView(currentDialog)
                                                currentRoot?.removeView(currentBlur)
                                            }
                                        }
                                        currentDialog?.setDismissListener {
                                            inAppCallback?.onInAppDismissed(inAppType.inAppId)
                                            currentRoot?.removeView(currentDialog)
                                            currentRoot?.removeView(currentBlur)
                                        }
                                        currentBlur?.setOnClickListener {
                                            inAppCallback?.onInAppDismissed(inAppType.inAppId)
                                            currentRoot?.removeView(currentDialog)
                                            currentRoot?.removeView(currentBlur)
                                        }
                                        isVisible = true
                                    }
                                    currentBlur?.isVisible = true
                                    MindboxLoggerImpl.d(this@InAppMessageViewDisplayerImpl,
                                        "inapp shown")
                                    onInAppShown()
                                }

                                override fun onError(e: Exception?) {
                                    MindboxLoggerImpl.e(
                                        parent = this@InAppMessageViewDisplayerImpl,
                                        message = "Failed to load inapp image",
                                        exception = e
                                            ?: RuntimeException("Failed to load inapp image")
                                    )
                                    currentRoot?.removeView(currentDialog)
                                    currentRoot?.removeView(currentBlur)
                                    this@with?.isVisible = false
                                }
                            })
                    }
                } else {
                    MindboxLoggerImpl.d(this, "in-app image url is blank")
                }
            }
            else -> {
                //TODO add inapp processing
            }
        }
        isInAppMessageActive = true
    }

    companion object {
        var isInAppMessageActive = false
    }
}

