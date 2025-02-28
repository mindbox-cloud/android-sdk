package cloud.mindbox.mobile_sdk.pushes.handler.image

/**
 * Default implementation of error handling on image loading
 */
public fun MindboxImageFailureHandler.Companion.default(): MindboxImageFailureHandler = DefaultStrategy

private val DefaultStrategy: MindboxImageFailureHandler = ApplyDefaultStrategyImpl()
