package cloud.mindbox.mobile_sdk.pushes.handler.image

/**
 * Default implementation of error handling on image loading
 */
fun MindboxImageFailureHandler.Companion.default(): MindboxImageFailureHandler = DefaultStrategy

private val DefaultStrategy: MindboxImageFailureHandler = ApplyDefaultStrategyImpl()
