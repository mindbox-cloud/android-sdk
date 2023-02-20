package cloud.mindbox.mobile_sdk.models

import cloud.mindbox.mobile_sdk.inapp.domain.models.CustomerSegmentationInApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.SegmentationCheckWrapper
import cloud.mindbox.mobile_sdk.models.operation.response.SegmentationCheckResponse

internal class SegmentationCheckInAppStub {
    companion object {
        fun getSegmentationCheckWrapper(): SegmentationCheckWrapper =
            SegmentationCheckWrapper("", listOf(getCustomerSegmentation()))

        fun getCustomerSegmentation(): CustomerSegmentationInApp =
            CustomerSegmentationInApp(segmentation = "", segment = "")

        fun getSegmentationCheckResponse(): SegmentationCheckResponse =
            SegmentationCheckResponse("", customerSegmentations = null)
    }

}