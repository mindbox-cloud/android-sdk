package cloud.mindbox.mobile_sdk.models

import cloud.mindbox.mobile_sdk.inapp.domain.models.ProductRequestDto
import cloud.mindbox.mobile_sdk.inapp.domain.models.ProductSegmentationRequestDto
import cloud.mindbox.mobile_sdk.inapp.domain.models.SegmentationRequestDto
import cloud.mindbox.mobile_sdk.inapp.domain.models.SegmentationRequestIds
import cloud.mindbox.mobile_sdk.models.operation.Ids

internal class ProductSegmentationRequestStub {

    companion object {

        fun getProductSegmentationRequestDto() = ProductSegmentationRequestDto(
            products = emptyList(),
            segmentations = emptyList()
        )

        fun getProductRequestDto() = ProductRequestDto(
            ids = Ids("" to "")
        )

        fun getSegmentationRequestDto() = SegmentationRequestDto(
            ids = SegmentationRequestIds("")
        )
    }
}
