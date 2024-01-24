package com.mindbox.example

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.URLUtil
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback

object CustomInAppCallback : InAppCallback {
    override fun onInAppClick(id: String, redirectUrl: String, payload: String) {
        Intent(Intent.ACTION_VIEW, Uri.parse(redirectUrl)).also { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (URLUtil.isValidUrl(redirectUrl) || intent.resolveActivity(ExampleApp.application.packageManager) != null) {
                ExampleApp.application.startActivity(intent)
            }
        }
        showToast(
            context = ExampleApp.application,
            message = "redirectUrl is :$redirectUrl and payload is :$payload"
        )
        Log.d(Utils.TAG, "onInAppClick id=$id, redirectUrl=$redirectUrl, payload=$payload")
    }

    override fun onInAppDismissed(id: String) {
        showToast(
            context = ExampleApp.application,
            message = "InApp dismissed"
        )
        Log.d(Utils.TAG, "onInAppDismissed id=$id")
    }
}