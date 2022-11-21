package cloud.mindbox.mobile_sdk.models.operation.response

internal class SegmentationCheckResponseStub {

    companion object {
        fun get() = SegmentationCheckResponse(status = null, customerSegmentations = listOf(
            CustomerSegmentationInAppResponse(segmentation = SegmentationInAppResponse(IdsResponse("12345")),
                segment = SegmentInAppResponse(
                    IdsResponse("12345")))))
    }
}