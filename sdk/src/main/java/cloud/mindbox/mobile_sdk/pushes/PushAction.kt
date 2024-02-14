package cloud.mindbox.mobile_sdk.pushes

import com.google.gson.annotations.SerializedName
/**
 * A class representing mindbox push action in [MindboxRemoteMessage]
 *  * You can use it as a model to store data from mindbox
 *  * with your custom push notification implementation.
 * */
data class PushAction(
    @SerializedName("uniqueKey") val uniqueKey: String?,
    @SerializedName("text") val text: String?,
    @SerializedName("url") val url: String?,
)