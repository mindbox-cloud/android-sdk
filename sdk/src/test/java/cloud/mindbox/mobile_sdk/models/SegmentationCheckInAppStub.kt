package cloud.mindbox.mobile_sdk.models

import cloud.mindbox.mobile_sdk.inapp.domain.models.*

internal class SegmentationCheckInAppStub {
    companion object {
        fun getSegmentationCheckInApp(): SegmentationCheckInApp =
            SegmentationCheckInApp("", listOf(getCustomerSegmentation()))

        fun getCustomerSegmentation(): CustomerSegmentationInApp =
            CustomerSegmentationInApp(segmentation = SegmentationInApp(IdsInApp(externalId = null)),
                segment = SegmentInApp(
                    IdsInApp(externalId = null
                    )))
    }
}