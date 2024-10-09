package cloud.mindbox.mobile_sdk.inapp.domain.interfaces

import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType

internal interface InAppContentFetcher {

    suspend fun fetchContent(inAppId: String, formVariant: InAppType): Boolean

    fun cancelFetching(inAppId: String)
}
