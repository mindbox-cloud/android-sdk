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

fun chooseInappCallback(selectedInappCallback: RegisterInappCallback) {
    when (selectedInappCallback) {
        //by default apply ComposableInAppCallback
        RegisterInappCallback.DEFAULT -> {}
        RegisterInappCallback.CUSTOM ->
            Mindbox.registerInAppCallback(CustomInAppCallback)

        RegisterInappCallback.CHOOSE_MINDBOX_CALLBACK -> Mindbox.registerInAppCallback(
            EmptyInAppCallback()
        )
    }
}

fun chooseNotificationImageHandler(selectedImageHandler: NotificationImageHandler) {
    when (selectedImageHandler) {
        //By default apply "applyDefaultStrategy" and default loader
        NotificationImageHandler.DEFAULT -> {}
        NotificationImageHandler.CUSTOM_LOADER -> {
            Mindbox.setMessageHandling(
                imageLoader = CustomImageLoader()
            )
        }

        NotificationImageHandler.CUSTOM_STRATEGY -> {
            Mindbox.setMessageHandling(
                imageFailureHandler = CustomImageFailureStrategy(
                    maxAttempts = 3, delay = 5
                )
            )
        }

        NotificationImageHandler.CHOOSE_MINDBOX_STRATEGY -> {
            Mindbox.setMessageHandling(
                imageFailureHandler = MindboxImageFailureHandler.retryOrDefaultStrategy(
                    maxAttempts = 3,
                    delay = 5,
                    defaultImage = Utils.defaultImage
                )
            )
        }
    }
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
