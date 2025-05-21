package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.CustomFields
import cloud.mindbox.mobile_sdk.models.operation.DateTime
import cloud.mindbox.mobile_sdk.models.operation.Ids
import cloud.mindbox.mobile_sdk.models.operation.adapters.DateTimeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

public open class PromoActionResponse(
    @SerializedName("ids") public val ids: Ids? = null,
    @SerializedName("name") public val name: String? = null,
    @SerializedName("description") public val description: String? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("startDateTimeUtc") public val startDateTimeUtc: DateTime? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("endDateTimeUtc") public val endDateTimeUtc: DateTime? = null,
    @SerializedName("customFields") public val customFields: CustomFields? = null,
    @SerializedName("limits") public val limits: List<LimitResponse>? = null
) {

    override fun toString(): String =
        "PromoActionResponse(ids=$ids, name=$name, description=$description, " +
            "startDateTimeUtc=$startDateTimeUtc, endDateTimeUtc=$endDateTimeUtc, " +
            "customFields=$customFields, limits=$limits)"
}
