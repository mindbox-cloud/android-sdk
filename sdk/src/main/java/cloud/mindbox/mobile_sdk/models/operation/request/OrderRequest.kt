package cloud.mindbox.mobile_sdk.models.operation.request

import cloud.mindbox.mobile_sdk.models.operation.CustomFields
import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName

public open class OrderRequest(
    @SerializedName("ids") public val ids: Ids? = null,
    @SerializedName("cashdesk") public val cashdesk: CashdeskRequest? = null,
    @SerializedName("deliveryCost") public val deliveryCost: Double? = null,
    @SerializedName("customFields") public val customFields: CustomFields? = null,
    @SerializedName("area") public val area: AreaRequest? = null,
    @SerializedName("totalPrice") public val totalPrice: Double? = null,
    @SerializedName("discounts") public val discounts: List<DiscountRequest>? = null,
    @SerializedName("lines") public val lines: List<LineRequest>? = null,
    @SerializedName("email") public val email: String? = null,
    @SerializedName("mobilePhone") public val mobilePhone: String? = null
)
