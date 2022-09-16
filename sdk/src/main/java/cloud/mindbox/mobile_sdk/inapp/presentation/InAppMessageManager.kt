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
import cloud.mindbox.mobile_sdk.inapp.domain.InAppType
import cloud.mindbox.mobile_sdk.inapp.presentation.view.InAppConstraintLayout
import com.squareup.picasso.Callback
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.Picasso


internal class InAppMessageManager {

    private var currentRoot: ViewGroup? = null
    private var currentBlur: View? = null
    private var currentDialog: InAppConstraintLayout? = null
    private var currentActivity: Activity? = null


    fun onResumeCurrentActivity(activity: Activity, shouldUseBlur: Boolean) {
        currentRoot = activity.window.decorView.findViewById(android.R.id.content)
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


    fun onPauseCurrentActivity(activity: Activity) {
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

    fun showInAppMessage(inAppType: InAppType) {
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
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(inAppType.redirectUrl))
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    currentActivity?.startActivity(browserIntent)
                }

                currentBlur?.setOnClickListener {
                    currentRoot?.removeView(currentDialog)
                    currentRoot?.removeView(currentBlur)
                }
                //TODO заменить URL
                Picasso.get()
                    .load(inAppType.imageUrl)
                    .memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE)
                    .fit()
                    .into(currentRoot?.findViewById(R.id.iv_content), object : Callback {
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

                        }

                    })
            }
            else -> {
                //TODO добавить обработку inapp
            }
        }
        isInAppMessageActive = true
    }

    fun showDefaultInAppMessage() {
        isInAppMessageActive = true
        currentRoot?.addView(currentBlur)
        currentRoot?.addView(currentDialog)
        currentDialog?.requestFocus()
        currentRoot?.findViewById<ImageView>(R.id.iv_close)?.setOnClickListener {
            currentRoot?.removeView(currentDialog)
            currentRoot?.removeView(currentBlur)
        }

        currentDialog?.setDismissListener {
            currentRoot?.removeView(currentDialog)
            currentRoot?.removeView(currentBlur)
        }
        currentDialog?.setOnClickListener {
            //TODO добавить прокидывание диплинка
        }

        currentBlur?.setOnClickListener {
            currentRoot?.removeView(currentDialog)
            currentRoot?.removeView(currentBlur)
        }
        //TODO заменить URL
        Picasso.get()
            .load("https://javasea.ru/uploads/posts/2018-08/1535401062_devochka-na-ograde-pri-zakate2.jpg")
            .memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE)
            .fit()
            .into(currentRoot?.findViewById(R.id.iv_content))
    }

    companion object {
        var isInAppMessageActive = false
    }
}

