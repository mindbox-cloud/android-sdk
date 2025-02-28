package cloud.mindbox.mobile_sdk.models.operation.request

import cloud.mindbox.mobile_sdk.models.operation.CustomFields
import cloud.mindbox.mobile_sdk.models.operation.DateOnly
import cloud.mindbox.mobile_sdk.models.operation.Ids
import cloud.mindbox.mobile_sdk.models.operation.Sex
import cloud.mindbox.mobile_sdk.models.operation.adapters.DateOnlyAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.util.TimeZone

public open class CustomerRequest private constructor(
    @SerializedName("authenticationTicket") public val authenticationTicket: String? = null,
    @SerializedName("discountCard") public val discountCard: DiscountCardRequest? = null,
    @JsonAdapter(DateOnlyAdapter::class)
    @SerializedName("birthDate") public val birthDate: DateOnly? = null,
    @SerializedName("sex") public val sex: Sex? = null,
    @SerializedName("timeZone") public val timeZone: String? = null,
    @SerializedName("lastName") public val lastName: String? = null,
    @SerializedName("firstName") public val firstName: String? = null,
    @SerializedName("middleName") public val middleName: String? = null,
    @SerializedName("fullName") public val fullName: String? = null,
    @SerializedName("area") public val area: AreaRequest? = null,
    @SerializedName("email") public val email: String? = null,
    @SerializedName("mobilePhone") public val mobilePhone: String? = null,
    @SerializedName("ids") public val ids: Ids? = null,
    @SerializedName("customFields") public val customFields: CustomFields? = null,
    @SerializedName("subscriptions") public val subscriptions: List<SubscriptionRequest>? = null
) {

    public constructor(
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

    public constructor(
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
