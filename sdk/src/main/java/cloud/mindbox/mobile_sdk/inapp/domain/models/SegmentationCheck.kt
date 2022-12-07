package cloud.mindbox.mobile_sdk.inapp.domain.models

internal data class SegmentationCheckInApp(
    val status: String,
    val customerSegmentations: List<CustomerSegmentationInApp>,
)

internal data class CustomerSegmentationInApp(
    val segmentation: String,
    val segment: String = NO_SEGMENT,
) {
    companion object {
        const val NO_SEGMENT = ""
    }
}
