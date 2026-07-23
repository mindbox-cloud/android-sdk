package com.mindbox.example

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.presentation.callbacks.EmptyInAppCallback
import cloud.mindbox.mobile_sdk.pushes.handler.image.MindboxImageFailureHandler
import cloud.mindbox.mobile_sdk.pushes.handler.image.retryOrDefaultStrategy


/**
 * Push click-URL that routes to [ActivityTransitionByPush] (the "second activity").
 * Used both in the `activities` map of the messaging services and in the screen's hint,
 * so the displayed value always matches the real routing.
 */
const val SECOND_ACTIVITY_PUSH_URL = "https://newActivity.com"

class Utils {
    companion object {
        const val TAG = "ExampleApp"
        val defaultImage =
            ContextCompat.getDrawable(ExampleApp.application, R.drawable.ic_launcher_background)
                ?.toBitmap()
    }
}

// Add push notification click event and return push data
// https://developers.mindbox.ru/docs/android-sdk-methods#onpushclicked
// https://developers.mindbox.ru/docs/android-sdk-methods#geturlfrompushintent
// https://developers.mindbox.ru/docs/android-sdk-methods#getpayloadfrompushintent
fun processMindboxIntent(intent: Intent?, context: Context): Pair<String?, String?>? {
    intent?.let {
        Mindbox.onPushClicked(context, it)
        return Mindbox.getUrlFromPushIntent(intent) to Mindbox.getPayloadFromPushIntent(intent)
    }
    return null
}

fun showToast(context: Context, message: String) {
    Toast.makeText(
        context,
        message,
        Toast.LENGTH_LONG
    ).show()
}

enum class NotificationImageHandler() {
    DEFAULT,
    CUSTOM_LOADER,
    CUSTOM_STRATEGY,
    CHOOSE_MINDBOX_STRATEGY
}

enum class RegisterInappCallback() {
    DEFAULT,
    CUSTOM,
    CHOOSE_MINDBOX_CALLBACK
}

enum class AsyncOperationType() {
    OPERATION_BODY,
    OPERATION_BODY_JSON
}

enum class SyncOperationType() {
    OPERATION_BODY,
    OPERATION_BODY_JSON,
    OPERATION_BODY_WITH_CUSTOM_RESPONSE
}
