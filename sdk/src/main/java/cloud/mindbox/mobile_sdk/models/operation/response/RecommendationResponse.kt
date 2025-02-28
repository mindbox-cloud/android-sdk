package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.CustomFields
import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName

public open class RecommendationResponse(
    @SerializedName("name") public val name: String? = null,
    @SerializedName("description") public val description: String? = null,
    @SerializedName("displayName") public val displayName: String? = null,
    @SerializedName("url") public val url: String? = null,
    @SerializedName("pictureUrl") public val pictureUrl: String? = null,
    @SerializedName("price") public val price: Double? = null,
    @SerializedName("oldPrice") public val oldPrice: Double? = null,
    @SerializedName("category") public val category: String? = null,
    @SerializedName("vendorCode") public val vendorCode: String? = null,
    @SerializedName("ids") public val ids: Ids? = null,
    @SerializedName("manufacturer") public val manufacturer: ManufacturerResponse? = null,
    @SerializedName("customFields") public val customFields: CustomFields? = null
) {

    override fun toString(): String = "RecommendationResponse(name=$name, description=$description, " +
        "displayName=$displayName, url=$url, pictureUrl=$pictureUrl, price=$price, " +
        "oldPrice=$oldPrice, category=$category, vendorCode=$vendorCode, ids=$ids, " +
        "manufacturer=$manufacturer, customFields=$customFields)"
}
