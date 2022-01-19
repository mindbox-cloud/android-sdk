package com.akkalomator.mindbox_hms

import cloud.mindbox.mobile_sdk_core.pushes.MindboxPushService
import cloud.mindbox.mobile_sdk_core.pushes.PushServiceHandler

object MindboxHuawei : MindboxPushService {

    override fun getServiceHandler(): PushServiceHandler = HuaweiServiceHandler

}