package cloud.mindbox.mobile_sdk.pushes

import cloud.mindbox.mobile_sdk.logger.MindboxLogger

interface MindboxPushService {

    fun getServiceHandler(logger: MindboxLogger): PushServiceHandler

}