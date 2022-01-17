package cloud.mindbox.mobile_sdk_core.models.operation.request

import com.google.gson.annotations.SerializedName

open class SubscriptionRequest(
	@SerializedName("isSubscribed") val isSubscribed: Boolean? = null,
	@SerializedName("brand") val brand: String? = null,
	@SerializedName("pointOfContact") val pointOfContact: PointOfContactRequest? = null,
	@SerializedName("topic") val topic: String? = null
)
