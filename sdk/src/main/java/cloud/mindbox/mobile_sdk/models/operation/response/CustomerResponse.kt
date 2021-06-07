package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.CustomFields
import cloud.mindbox.mobile_sdk.models.operation.DateOnly
import cloud.mindbox.mobile_sdk.models.operation.Ids
import cloud.mindbox.mobile_sdk.models.operation.Sex
import cloud.mindbox.mobile_sdk.models.operation.adapters.DateOnlyAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

open class CustomerResponse private constructor(
    @SerializedName("discountCard") val discountCard: DiscountCardResponse? = null,
    @JsonAdapter(DateOnlyAdapter::class)
    @SerializedName("birthDate") val birthDate: DateOnly? = null,
    @SerializedName("sex") val sex: Sex? = null,
    @SerializedName("timeZone") val timeZone: String? = null,
    @SerializedName("lastName") val lastName: String? = null,
    @SerializedName("firstName") val firstName: String? = null,
    @SerializedName("middleName") val middleName: String? = null,
    @SerializedName("fullName") val fullName: String? = null,
    @SerializedName("area") val area: AreaResponse? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("mobilePhone") val mobilePhone: String? = null,
    @SerializedName("ids") val ids: Ids? = null,
    @SerializedName("customFields") val customFields: CustomFields? = null,
    @SerializedName("subscriptions") val subscriptions: List<SubscriptionResponse>? = null
) {

    override fun toString() = "CustomerResponse(discountCard=$discountCard, " +
            "birthDate=$birthDate, sex=$sex, timeZone=$timeZone, lastName=$lastName, " +
            "firstName=$firstName, middleName=$middleName, fullName=$fullName, area=$area, " +
            "email=$email, mobilePhone=$mobilePhone, ids=$ids, customFields=$customFields, " +
            "subscriptions=$subscriptions)"

}
