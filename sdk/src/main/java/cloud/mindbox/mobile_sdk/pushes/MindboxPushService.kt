package cloud.mindbox.mobile_sdk.pushes

import androidx.annotation.RestrictTo
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler

/**
 * An interface for internal sdk work only. Do not implement it
 * */
public interface MindboxPushService {

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val tag: String

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val trackingIdType: String?
        get() = null

    public fun getServiceHandler(
        logger: MindboxLogger,
        exceptionHandler: ExceptionHandler,
    ): PushServiceHandler
}
