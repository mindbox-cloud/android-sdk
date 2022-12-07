package cloud.mindbox.mobile_sdk.inapp.domain.models

internal data class SegmentationCheckInApp(
    val status: String,
    val customerSegmentations: List<CustomerSegmentationInApp>,
)

internal data class CustomerSegmentationInApp(
    val segmentation: SegmentationInApp?,
    val segment: SegmentInApp?,
)

internal data class IdsInApp(
    val externalId: String?,
)

internal data class SegmentationInApp(
    val ids: IdsInApp?,
)

internal data class SegmentInApp(
    val ids: IdsInApp?,
)