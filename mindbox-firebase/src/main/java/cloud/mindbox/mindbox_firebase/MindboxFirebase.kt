package cloud.mindbox.mindbox_firebase

import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.pushes.MindboxPushService
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
/**
 * An object to use when choosing push provider in Mindbox.initPushServices or Mindbox.init.
 * Represents FCM
 * */
object MindboxFirebase : MindboxPushService {

    override val tag: String = "FCM"

    override fun getServiceHandler(
        logger: MindboxLogger,
        exceptionHandler: ExceptionHandler,
    ): PushServiceHandler = FirebaseServiceHandler(logger, exceptionHandler)

    override fun toString(): String = tag
}