package cloud.mindbox.mobile_sdk.models.operation.request

import cloud.mindbox.mobile_sdk.models.operation.CustomFields
import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName

open class OrderRequest(
    @SerializedName("ids") val ids: Ids? = null,
    @SerializedName("cashdesk") val cashdesk: CashdeskRequest? = null,
    @SerializedName("deliveryCost") val deliveryCost: Double? = null,
    @SerializedName("customFields") val customFields: CustomFields? = null,
    @SerializedName("area") val area: AreaRequest? = null,
    @SerializedName("totalPrice") val totalPrice: Double? = null,
    @SerializedName("discounts") val discounts: List<DiscountRequest>? = null,
    @SerializedName("lines") val lines: List<LineRequest>? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("mobilePhone") val mobilePhone: String? = null
)
