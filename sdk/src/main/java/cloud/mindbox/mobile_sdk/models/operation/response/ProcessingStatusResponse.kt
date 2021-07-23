package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

enum class ProcessingStatusResponse {

    @SerializedName("Success") SUCCESS,
    @SerializedName("Processed") PROCESSED,
    @SerializedName("Found") FOUND,
    @SerializedName("Created") CREATED,
    @SerializedName("Changed") CHANGED,
    @SerializedName("Updated") UPDATED,
    @SerializedName("Calculated") CALCULATED,
    @SerializedName("AlreadyExists")ALREADY_EXISTS,
    @SerializedName("Ambiguous") AMBIGUOUS,
    @SerializedName("NotChanged") NOT_CHANGED,
    @SerializedName("NotFound") NOT_FOUND,
    @SerializedName("Deleted") DELETED,
    @SerializedName("RequiredEntityMissingFromResponse") REQUIRED_ENTITY_MISSING_FROM_RESPONSE,
    @SerializedName("ProtocolError") PROTOCOL_ERROR,
    @SerializedName("ValidationError") VALIDATION_ERROR,
    @SerializedName("MindboxServerError") MINDBOX_SERVER_ERROR,
    @SerializedName("PriceHasBeenChanged") PRICE_HAS_BEEN_CHANGED,
    @SerializedName("PersonalDiscountsCalculationIsUnavailable") PERSONAL_DISCOUNTS_CALCULATION_IS_UNAVAILABLE,
    @SerializedName("DiscountsCalculationIsUnavailable") DISCOUNTS_CALCULATION_IS_UNAVAILABLE,
    @SerializedName("InvalidAuthenticationTicket") INVALID_AUTHENTICATION_TICKET,
    @SerializedName("NotProcessed") NOT_PROCESSED

}
