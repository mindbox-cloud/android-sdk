package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.CustomFields
import cloud.mindbox.mobile_sdk.models.operation.Ids
import com.google.gson.annotations.SerializedName

open class RecommendationResponse(
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("pictureUrl") val pictureUrl: String? = null,
    @SerializedName("price") val price: Double? = null,
    @SerializedName("oldPrice") val oldPrice: Double? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("vendorCode") val vendorCode: String? = null,
    @SerializedName("ids") val ids: Ids? = null,
    @SerializedName("manufacturer") val manufacturer: ManufacturerResponse? = null,
    @SerializedName("customFields") val customFields: CustomFields? = null
) {

    override fun toString() = "RecommendationResponse(name=$name, description=$description, " +
            "displayName=$displayName, url=$url, pictureUrl=$pictureUrl, price=$price, " +
            "oldPrice=$oldPrice, category=$category, vendorCode=$vendorCode, ids=$ids, " +
            "manufacturer=$manufacturer, customFields=$customFields)"

}
