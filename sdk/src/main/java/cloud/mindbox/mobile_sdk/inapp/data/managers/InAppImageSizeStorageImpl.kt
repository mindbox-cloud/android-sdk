package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageSizeStorage
import cloud.mindbox.mobile_sdk.inapp.domain.models.Size
import java.util.concurrent.ConcurrentHashMap

internal class InAppImageSizeStorageImpl : InAppImageSizeStorage {

    private val storage: ConcurrentHashMap<String, ConcurrentHashMap<String, Size>> =
        ConcurrentHashMap()

    override fun getSizeByIdAndUrl(id: String, url: String): Size = storage[id]?.get(url) ?: Size(0, 0)

    override fun addSize(
        id: String,
        url: String,
        width: Int,
        height: Int
    ) {
        storage[id] = storage
            .getOrElse(id) {
                ConcurrentHashMap()
            }.also { map ->
                map[url] = Size(width, height)
            }
    }
}
