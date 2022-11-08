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
import cloud.mindbox.mobile_sdk.inapp.domain.InAppType
import cloud.mindbox.mobile_sdk.inapp.presentation.view.InAppConstraintLayout
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
        currentRoot = activity.window.decorView.rootView as ViewGroup
        currentBlur = if (shouldUseBlur) {
            LayoutInflater.from(activity).inflate(R.layout.blur_layout,
                currentRoot, false
            )
        } else {
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
        currentRoot = activity.window.decorView.rootView as ViewGroup
        currentBlur = if (shouldUseBlur) {
            LayoutInflater.from(activity).inflate(R.layout.blur_layout,
                currentRoot, false
            )
        } else {
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
        if (currentActivity == activity) {
            currentActivity = null
        }
        hideDefaultInAppMessage()
    }

    private fun hideDefaultInAppMessage() {
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
                    currentRoot?.addView(currentBlur)
                    currentRoot?.addView(currentDialog)
                    currentDialog?.requestFocus()

                    currentDialog?.setDismissListener {
                        inAppCallback?.onInAppDismissed(inAppType.inAppId)
                        currentRoot?.removeView(currentDialog)
                        currentRoot?.removeView(currentBlur)
                    }
                    with(currentRoot?.findViewById<ImageView>(R.id.iv_content)) {
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
                                        currentBlur?.setOnClickListener {
                                            inAppCallback?.onInAppDismissed(inAppType.inAppId)
                                            currentRoot?.removeView(currentDialog)
                                            currentRoot?.removeView(currentBlur)
                                        }
                                        isVisible = true
                                    }
                                    currentBlur?.isVisible = true
                                    onInAppShown()
                                }

                                override fun onError(e: Exception?) {
                                    currentRoot?.removeView(currentDialog)
                                    currentRoot?.removeView(currentBlur)
                                    this@with?.isVisible = false
                                }

                            })
                    }
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
        private const val LOG_TAG = "InAppMessageViewDisplayerImpl"
    }
}

