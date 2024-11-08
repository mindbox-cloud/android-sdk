package cloud.mindbox.mobile_sdk.pushes.handler

import cloud.mindbox.mobile_sdk.pushes.handler.image.MindboxImageFailureHandler
import cloud.mindbox.mobile_sdk.pushes.handler.image.MindboxImageLoader
import cloud.mindbox.mobile_sdk.pushes.handler.image.default

internal data class MindboxMessageHandler(
    val imageFailureHandler: MindboxImageFailureHandler = MindboxImageFailureHandler.default(),
    val imageLoader: MindboxImageLoader = MindboxImageLoader.default(),
)
