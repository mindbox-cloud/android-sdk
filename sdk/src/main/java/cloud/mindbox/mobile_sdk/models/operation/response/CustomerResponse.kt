package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.*
import cloud.mindbox.mobile_sdk.models.operation.adapters.DateOnlyAdapter
import cloud.mindbox.mobile_sdk.models.operation.adapters.DateTimeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

public open class CustomerResponse private constructor(
    @SerializedName("discountCard") public val discountCard: DiscountCardResponse? = null,
    @JsonAdapter(DateOnlyAdapter::class)
    @SerializedName("birthDate") public val birthDate: DateOnly? = null,
    @SerializedName("sex") public val sex: Sex? = null,
    @SerializedName("timeZone") public val timeZone: String? = null,
    @SerializedName("lastName") public val lastName: String? = null,
    @SerializedName("firstName") public val firstName: String? = null,
    @SerializedName("middleName") public val middleName: String? = null,
    @SerializedName("fullName") public val fullName: String? = null,
    @SerializedName("area") public val area: AreaResponse? = null,
    @SerializedName("email") public val email: String? = null,
    @SerializedName("mobilePhone") public val mobilePhone: String? = null,
    @SerializedName("ids") public val ids: Ids? = null,
    @SerializedName("customFields") public val customFields: CustomFields? = null,
    @SerializedName("subscriptions") public val subscriptions: List<SubscriptionResponse>? = null,
    @SerializedName("processingStatus") public val processingStatus: ProcessingStatusResponse? = null,
    @SerializedName("isEmailInvalid") public val isEmailInvalid: Boolean? = null,
    @SerializedName("isMobilePhoneInvalid") public val isMobilePhoneInvalid: Boolean? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("changeDateTimeUtc") public val changeDateTimeUtc: DateTime? = null,
    @SerializedName("ianaTimeZone") public val ianaTimeZone: String? = null,
    @SerializedName("timeZoneSource") public val timeZoneSource: String? = null
) {

    override fun toString(): String = "CustomerResponse(discountCard=$discountCard, " +
        "birthDate=$birthDate, sex=$sex, timeZone=$timeZone, lastName=$lastName, " +
        "firstName=$firstName, middleName=$middleName, fullName=$fullName, area=$area, " +
        "email=$email, mobilePhone=$mobilePhone, ids=$ids, customFields=$customFields, " +
        "subscriptions=$subscriptions, processingStatus=$processingStatus, " +
        "isEmailInvalid=$isEmailInvalid, isMobilePhoneInvalid=$isMobilePhoneInvalid, " +
        "changeDateTimeUtc=$changeDateTimeUtc, ianaTimeZone=$ianaTimeZone, " +
        "timeZoneSource=$timeZoneSource)"
}
