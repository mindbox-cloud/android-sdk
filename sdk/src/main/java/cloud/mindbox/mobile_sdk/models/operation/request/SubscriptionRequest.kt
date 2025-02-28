package cloud.mindbox.mobile_sdk.models.operation.request

import com.google.gson.annotations.SerializedName

public open class SubscriptionRequest(
    @SerializedName("isSubscribed") public val isSubscribed: Boolean? = null,
    @SerializedName("brand") public val brand: String? = null,
    @SerializedName("pointOfContact") public val pointOfContact: PointOfContactRequest? = null,
    @SerializedName("topic") public val topic: String? = null
)
