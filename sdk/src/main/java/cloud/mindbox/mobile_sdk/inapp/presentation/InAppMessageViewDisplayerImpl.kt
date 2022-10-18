package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
                setBackgroundColor(ContextCompat.getColor(activity, android.R.color.transparent))
            }
        }
        currentActivity = activity
        currentDialog = LayoutInflater.from(activity).inflate(R.layout.default_inapp_layout,
            currentRoot, false
        ) as InAppConstraintLayout
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

    override fun showInAppMessage(inAppType: InAppType) {
        when (inAppType) {
            is InAppType.SimpleImage -> {
                currentRoot?.addView(currentBlur)
                currentRoot?.addView(currentDialog)
                currentDialog?.requestFocus()

                currentDialog?.setDismissListener {
                    currentRoot?.removeView(currentDialog)
                    currentRoot?.removeView(currentBlur)
                }
                currentDialog?.setOnClickListener {
                    val browserIntent =
                        Intent(Intent.ACTION_VIEW,
                            Uri.parse(inAppType.redirectUrl)).putExtra("intentData",
                            inAppType.intentData)
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    currentActivity?.startActivity(browserIntent)
                }
                currentBlur?.setOnClickListener {
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
                                        currentRoot?.removeView(currentDialog)
                                        currentRoot?.removeView(currentBlur)
                                    }
                                    isVisible = true
                                }
                            }

                            override fun onError(e: Exception?) {
                                currentRoot?.removeView(currentDialog)
                                currentRoot?.removeView(currentBlur)
                                this@with?.isVisible = false
                            }

                        })
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

