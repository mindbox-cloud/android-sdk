package com.mindbox.example

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import cloud.mindbox.mobile_sdk.pushes.RemoteMessage
import cloud.mindbox.mobile_sdk.pushes.handler.MessageHandlingState
import cloud.mindbox.mobile_sdk.pushes.handler.image.MindboxImageLoader
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import java.util.concurrent.CountDownLatch

class CustomImageLoader : MindboxImageLoader {

    override fun onLoadImage(
        context: Context,
        message: RemoteMessage,
        state: MessageHandlingState
    ): Bitmap? {
        val imageUrl = message.imageUrl
        Log.d(Utils.TAG, "Used custom loader")
        Log.d(Utils.TAG, "Image url $imageUrl")
        if (imageUrl.isNullOrBlank()) return null
        val latch = CountDownLatch(1)
        var resultBitmap: Bitmap? = null
        val handler = Handler(Looper.getMainLooper())
        try {
            Glide.with(context)
                .asBitmap()
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(object : SimpleTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        handler.post {
                            resultBitmap = resource
                            latch.countDown()
                        }
                    }
                })
            latch.await()
        } catch (e: Exception) {
            Log.e(Utils.TAG, "Error waiting for image loading", e)
        } finally {
            return resultBitmap
        }
    }
}
