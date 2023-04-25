package cloud.mindbox.mobile_sdk.inapp.domain.interfaces

interface InAppImageLoader {

    suspend fun loadImage(url: String): Boolean
}