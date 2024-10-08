package cloud.mindbox.mobile_sdk.inapp.domain.interfaces

interface InAppImageLoader {

    suspend fun loadImage(inAppId: String, url: String): Boolean

    fun cancelLoading(inAppId: String)
}
