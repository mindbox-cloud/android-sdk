package cloud.mindbox.mobile_sdk.inapp

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.annotation.Nullable
import androidx.core.content.ContextCompat
import cloud.mindbox.mobile_sdk.R
import com.squareup.picasso.Picasso


internal class InAppMessageManager {

    private var currentRoot: ViewGroup? = null
    private var currentBlur: View? = null
    private var currentDialog: InAppFrameLayout? = null


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
        currentDialog = LayoutInflater.from(activity).inflate(R.layout.default_inapp_layout,
            currentRoot, false
        ) as InAppFrameLayout
        showDefaultInAppMessage()
    }

    fun onPauseCurrentActivity() {
        hideDefaultInAppMessage()
    }

    private fun hideDefaultInAppMessage() {
        currentRoot?.removeView(currentBlur)
        currentRoot?.removeView(currentDialog)
    }

    private fun showDefaultInAppMessage() {

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
        currentDialog?.setOnClickListener{
            //TODO добавить прокидывание диплинка
        }

        currentBlur?.setOnClickListener {
            currentRoot?.removeView(currentDialog)
            currentRoot?.removeView(currentBlur)
        }
        //TODO заменить URL
        Picasso.get()
            .load("https://ichip.ru/images/cache/2019/10/31/fit_930_519_false_crop_1280_720_0_0_q90_355392_3fa9176a91.jpeg")
            .fit().into(currentRoot?.findViewById(R.id.iv_content))
    }
}

