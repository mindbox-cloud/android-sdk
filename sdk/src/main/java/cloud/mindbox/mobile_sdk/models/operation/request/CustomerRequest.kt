package cloud.mindbox.mobile_sdk.models.operation.request

import cloud.mindbox.mobile_sdk.models.operation.CustomFields
import cloud.mindbox.mobile_sdk.models.operation.DateOnly
import cloud.mindbox.mobile_sdk.models.operation.Ids
import cloud.mindbox.mobile_sdk.models.operation.Sex
import cloud.mindbox.mobile_sdk.models.operation.adapters.DateOnlyAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.util.TimeZone

open class CustomerRequest private constructor(
    @SerializedName("authenticationTicket") val authenticationTicket: String? = null,
    @SerializedName("discountCard") val discountCard: DiscountCardRequest? = null,
    @JsonAdapter(DateOnlyAdapter::class)
    @SerializedName("birthDate") val birthDate: DateOnly? = null,
    @SerializedName("sex") val sex: Sex? = null,
    @SerializedName("timeZone") val timeZone: String? = null,
    @SerializedName("lastName") val lastName: String? = null,
    @SerializedName("firstName") val firstName: String? = null,
    @SerializedName("middleName") val middleName: String? = null,
    @SerializedName("fullName") val fullName: String? = null,
    @SerializedName("area") val area: AreaRequest? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("mobilePhone") val mobilePhone: String? = null,
    @SerializedName("ids") val ids: Ids? = null,
    @SerializedName("customFields") val customFields: CustomFields? = null,
    @SerializedName("subscriptions") val subscriptions: List<SubscriptionRequest>? = null
) {

    constructor(
        authenticationTicket: String? = null,
        discountCard: DiscountCardRequest? = null,
        birthDate: DateOnly? = null,
        sex: Sex? = null,
        timeZone: TimeZone? = null,
        lastName: String? = null,
        firstName: String? = null,
        middleName: String? = null,
        area: AreaRequest? = null,
        email: String? = null,
        mobilePhone: String? = null,
        ids: Ids? = null,
        customFields: CustomFields? = null,
        subscriptions: List<SubscriptionRequest>? = null
    ) : this(
        authenticationTicket = authenticationTicket,
        discountCard = discountCard,
        birthDate = birthDate,
        sex = sex,
        timeZone = timeZone?.id,
        lastName = lastName,
        firstName = firstName,
        middleName = middleName,
        area = area,
        email = email,
        mobilePhone = mobilePhone,
        ids = ids,
        customFields = customFields,
        subscriptions = subscriptions
    )

    constructor(
        authenticationTicket: String? = null,
        discountCard: DiscountCardRequest? = null,
        birthDate: DateOnly? = null,
        sex: Sex? = null,
        timeZone: TimeZone? = null,
        fullName: String? = null,
        area: AreaRequest? = null,
        email: String? = null,
        mobilePhone: String? = null,
        ids: Ids? = null,
        customFields: CustomFields? = null,
        subscriptions: List<SubscriptionRequest>? = null
    ) : this(
        authenticationTicket = authenticationTicket,
        discountCard = discountCard,
        birthDate = birthDate,
        sex = sex,
        timeZone = timeZone?.id,
        fullName = fullName,
        area = area,
        email = email,
        mobilePhone = mobilePhone,
        ids = ids,
        customFields = customFields,
        subscriptions = subscriptions
    )
}
