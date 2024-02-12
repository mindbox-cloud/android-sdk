package cloud.mindbox.mindbox_firebase

import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import com.google.firebase.messaging.RemoteMessage as FirebaseRemoteMessage

internal class FirebaseRemoteMessageTransformer(private val exceptionHandler: ExceptionHandler) {


    fun transform(
        remoteMessage: FirebaseRemoteMessage?,
    ): MindboxRemoteMessage? = exceptionHandler.runCatching(defaultValue = null) {
        MindboxFirebase.convertToMindboxRemoteMessage(remoteMessage)
    }


}