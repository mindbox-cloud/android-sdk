package cloud.mindbox.mobile_sdk.inapp.domain.interfaces

import cloud.mindbox.mobile_sdk.inapp.domain.models.Payload

internal interface InAppContentFetcher {

    suspend fun fetchContent(formVariant: Payload): Boolean
}