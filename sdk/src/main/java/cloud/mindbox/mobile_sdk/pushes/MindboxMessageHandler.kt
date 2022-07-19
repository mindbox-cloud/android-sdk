package cloud.mindbox.mobile_sdk.pushes

internal data class MindboxMessageHandler(
    val imageFailureHandler: MindboxImageFailureHandler = MindboxImageFailureHandler.Default,
    val imageLoader: MindboxImageLoader = MindboxImageLoader.Default,
)