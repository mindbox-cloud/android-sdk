package cloud.mindbox.mobile_sdk_core.models.operation.response

import cloud.mindbox.mobile_sdk_core.models.operation.CustomFields
import cloud.mindbox.mobile_sdk_core.models.operation.Ids
import com.google.gson.annotations.SerializedName

open class ProductResponse(
    @SerializedName("ids") val ids: Ids? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("pictureUrl") val pictureUrl: String? = null,
    @SerializedName("price") val price: Double? = null,
    @SerializedName("oldPrice") val oldPrice: Double? = null,
    @SerializedName("customFields") val customFields: CustomFields? = null
) {

    override fun toString() =
        "ProductResponse(ids=$ids, name=$name, displayName=$displayName, " +
                "url=$url, pictureUrl=$pictureUrl, price=$price, " +
                "oldPrice=$oldPrice, customFields=$customFields)"

}
