package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.CustomFields
import cloud.mindbox.mobile_sdk.models.operation.DateTime
import cloud.mindbox.mobile_sdk.models.operation.Ids
import cloud.mindbox.mobile_sdk.models.operation.adapters.DateTimeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

open class PromoActionResponse(
    @SerializedName("ids") val ids: Ids? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("startDateTimeUtc") val startDateTimeUtc: DateTime? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("endDateTimeUtc") val endDateTimeUtc: DateTime? = null,
    @SerializedName("customFields") val customFields: CustomFields? = null,
    @SerializedName("limits") val limits: List<LimitResponse>? = null
) {
    override fun toString(): String {
        return "PromoActionResponse(ids=$ids, name=$name, description=$description, " +
                "startDateTimeUtc=$startDateTimeUtc, endDateTimeUtc=$endDateTimeUtc, " +
                "customFields=$customFields, limits=$limits)"
    }
}