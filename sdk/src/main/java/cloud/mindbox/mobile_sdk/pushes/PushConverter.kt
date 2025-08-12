package cloud.mindbox.mobile_sdk.pushes

internal interface PushConverter {
    fun convertToRemoteMessage(message: Any): MindboxRemoteMessage?
}
