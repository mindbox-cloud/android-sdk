package cloud.mindbox.mobile_sdk.inapp.domain.interfaces

import cloud.mindbox.mobile_sdk.inapp.domain.models.Size


internal interface InAppImageSizeStorage {

    fun getSizeByIdAndUrl(id: String, url: String): Size

    fun addSize(id: String, url: String, width: Int, height: Int)
}