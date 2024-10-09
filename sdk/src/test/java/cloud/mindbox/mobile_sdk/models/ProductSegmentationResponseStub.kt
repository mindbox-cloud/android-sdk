package cloud.mindbox.mobile_sdk.models

import cloud.mindbox.mobile_sdk.inapp.domain.models.*

internal class ProductSegmentationResponseStub {

    companion object {
        fun getProductSegmentationResponseDto() =
            ProductSegmentationResponseDto(status = "", products = emptyList())

        fun getProductResponseDto() = ProductResponseDto(
            ids = null,
            segmentations = emptyList()
        )

        fun getSegmentationResponseDto() = SegmentationResponseDto(
            ids = null, segment = getSegmentResponseDto()
        )

        fun getSegmentResponseDto() = SegmentResponseDto(
            ids = null
        )

        fun getProductSegmentationResponseWrapper() = ProductSegmentationResponseWrapper(
            productSegmentations = listOf(getProductResponse())
        )

        fun getProductResponse() = ProductResponse(listOf(getProductSegmentationsResponse()))

        fun getProductSegmentationsResponse() = ProductSegmentationResponse(
            segmentationExternalId = "",
            segmentExternalId = ""
        )
    }
}
