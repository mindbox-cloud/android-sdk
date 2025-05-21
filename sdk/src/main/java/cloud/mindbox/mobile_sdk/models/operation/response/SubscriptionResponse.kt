package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

public open class SubscriptionResponse(
    @SerializedName("isSubscribed") public val isSubscribed: Boolean? = null,
    @SerializedName("brand") public val brand: String? = null,
    @SerializedName("pointOfContact") public val pointOfContact: PointOfContactResponse? = null,
    @SerializedName("topic") public val topic: String? = null
) {

    override fun toString(): String = "SubscriptionResponse(isSubscribed=$isSubscribed, brand=$brand, " +
        "pointOfContact=$pointOfContact, topic=$topic)"
}
