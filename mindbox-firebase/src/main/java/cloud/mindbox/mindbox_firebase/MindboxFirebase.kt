package cloud.mindbox.mindbox_firebase

import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.pushes.MindboxPushService
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler

object MindboxFirebase : MindboxPushService {

    override val tag: String = "FCM"

    override fun getServiceHandler(
        logger: MindboxLogger,
        exceptionHandler: ExceptionHandler,
    ): PushServiceHandler = FirebaseServiceHandler(logger, exceptionHandler)

    override fun toString(): String = tag
}