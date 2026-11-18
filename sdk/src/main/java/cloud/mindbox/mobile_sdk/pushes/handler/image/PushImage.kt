package cloud.mindbox.mobile_sdk.pushes.handler.image

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.pushes.handler.MessageHandlingState

internal data class PushImage(
    val bitmap: Bitmap,
    val animatedImageUri: Uri? = null,
)

internal interface PushImageLoader {

    fun loadPushImage(
        context: Context,
        message: MindboxRemoteMessage,
        state: MessageHandlingState,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): PushImage?
}
