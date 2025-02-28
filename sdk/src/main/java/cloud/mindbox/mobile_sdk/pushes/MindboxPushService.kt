package cloud.mindbox.mobile_sdk.pushes

import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler

/**
 * An interface for internal sdk work only. Do not implement it
 * */
public interface MindboxPushService {

    public val tag: String

    public fun getServiceHandler(
        logger: MindboxLogger,
        exceptionHandler: ExceptionHandler,
    ): PushServiceHandler
}
