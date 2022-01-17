package cloud.mindbox.mobile_sdk_core.models.operation.response

import com.google.gson.annotations.SerializedName

open class SubscriptionResponse(
	@SerializedName("isSubscribed") val isSubscribed: Boolean? = null,
	@SerializedName("brand") val brand: String? = null,
	@SerializedName("pointOfContact") val pointOfContact: PointOfContactResponse? = null,
	@SerializedName("topic") val topic: String? = null
) {

    override fun toString() = "SubscriptionResponse(isSubscribed=$isSubscribed, brand=$brand, " +
            "pointOfContact=$pointOfContact, topic=$topic)"

}
