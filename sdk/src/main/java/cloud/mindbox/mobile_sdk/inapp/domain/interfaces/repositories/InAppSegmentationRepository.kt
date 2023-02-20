package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories

import cloud.mindbox.mobile_sdk.inapp.domain.models.CustomerSegmentationInApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.SegmentationFetchStatus

internal interface InAppSegmentationRepository {

    var unShownInApps: List<InApp>

    suspend fun fetchSegmentations()

    fun setSegmentationStatus(status: SegmentationFetchStatus)

    fun getSegmentationFetched(): SegmentationFetchStatus

    fun getSegmentations(): List<CustomerSegmentationInApp>
}