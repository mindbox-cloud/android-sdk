package cloud.mindbox.mobile_sdk.models.operation.request

import com.google.gson.annotations.SerializedName

internal data class InAppShowFailure(
    @SerializedName("inappId")
    val inAppId: String,
    @SerializedName("failureReason")
    val failureReason: FailureReason,
    @SerializedName("errorDetails")
    val errorDetails: String?,
    @SerializedName("timestamp")
    val timestamp: String
)

internal enum class FailureReason(val value: String) {
    @SerializedName("image_download_failed")
    IMAGE_DOWNLOAD_FAILED("image_download_failed"),

    @SerializedName("presentation_failed")
    PRESENTATION_FAILED("presentation_failed"),

    @SerializedName("geo_request_failed")
    GEO_TARGETING_FAILED("geo_request_failed"),

    @SerializedName("customer_segment_request_failed")
    CUSTOMER_SEGMENT_REQUEST_FAILED("customer_segment_request_failed"),

    @SerializedName("product_segmentation_request_failed")
    PRODUCT_SEGMENT_REQUEST_FAILED("product_segmentation_request_failed"),

    @SerializedName("html_load_failed")
    HTML_LOAD_FAILED("html_load_failed"),

    @SerializedName("webview_init_failed")
    WEBVIEW_INIT_FAILED("webview_init_failed"),

    @SerializedName("unknown_error")
    UNKNOWN_ERROR("unknown_error")
}
