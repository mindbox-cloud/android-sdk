package cloud.mindbox.mobile_sdk_core.pushes.firebase

import cloud.mindbox.mobile_sdk_core.pushes.MindboxPushService
import cloud.mindbox.mobile_sdk_core.pushes.PushServiceHandler

object MindboxFirebase : MindboxPushService {

    override fun getServiceHandler(): PushServiceHandler = FirebaseServiceHandler

}