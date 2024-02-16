package com.mindbox.example

import android.content.Context
import android.util.Log
import cloud.mindbox.mobile_sdk.pushes.RemoteMessage
import cloud.mindbox.mobile_sdk.pushes.handler.MessageHandlingState
import cloud.mindbox.mobile_sdk.pushes.handler.image.ImageRetryStrategy
import cloud.mindbox.mobile_sdk.pushes.handler.image.MindboxImageFailureHandler

class CustomImageFailureStrategy(
    private val maxAttempts: Int,
    private val delay: Long = 0L
) : MindboxImageFailureHandler {
    override fun onImageLoadingFailed(
        context: Context,
        message: RemoteMessage,
        state: MessageHandlingState,
        error: Throwable
    ): ImageRetryStrategy = if (state.attemptNumber >= maxAttempts) {
        Log.d(Utils.TAG, "Attempts are over. Download image canceled")
        ImageRetryStrategy.Cancel

    } else {
        Log.d(Utils.TAG, "Current attempt ${state.attemptNumber}")
        Log.d(Utils.TAG, "Max attempts $maxAttempts")
        ImageRetryStrategy.Retry(delay = delay)
    }
}