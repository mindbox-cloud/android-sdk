package cloud.mindbox.mobile_sdk_core.models.operation.response

import com.google.gson.annotations.SerializedName

enum class IssueStatusResponse {

    @SerializedName("Received") RECEIVED,
    @SerializedName("PromoCodeNotFound") PROMO_CODE_NOT_FOUND,
    @SerializedName("PromoCodePoolNotFound") PROMO_CODE_POOL_NOT_FOUND,
    @SerializedName("NoAvailablePromoCodesInPool") NOT_AVAILABLE_PROMO_CODES_IN_POOL,
    @SerializedName("NotInIssueDateTimeRange") NOT_IN_ISSUE_DATE_TIME_RANGE,
    @SerializedName("NotAvailableForIssue") NOT_AVAILABLE_FOR_ISSUE,
    @SerializedName("Issued") ISSUED

}
