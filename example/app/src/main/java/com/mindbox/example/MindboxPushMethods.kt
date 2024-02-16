package com.mindbox.example

import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.pushes.handler.image.MindboxImageFailureHandler
import cloud.mindbox.mobile_sdk.pushes.handler.image.retryOrDefaultStrategy

//https://developers.mindbox.ru/docs/android-sdk-methods#setmessagehandling-since-261
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