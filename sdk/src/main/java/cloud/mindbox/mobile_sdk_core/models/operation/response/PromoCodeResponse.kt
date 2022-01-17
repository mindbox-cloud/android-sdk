package cloud.mindbox.mobile_sdk_core.models.operation.response

import cloud.mindbox.mobile_sdk_core.models.operation.DateTime
import cloud.mindbox.mobile_sdk_core.models.operation.Ids
import cloud.mindbox.mobile_sdk_core.models.operation.adapters.DateTimeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

open class PromoCodeResponse(
    @SerializedName("issueStatus") val issueStatus: IssueStatusResponse? = null,
    @SerializedName("ids") val ids: Ids? = null,
    @SerializedName("pool") val pool: PoolResponse? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("availableFromDateTimeUtc") val availableFromDateTimeUtc: DateTime? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("availableTillDateTimeUtc") val availableTillDateTimeUtc: DateTime? = null,
    @SerializedName("isUsed") val isUsed: Boolean? = null,
    @SerializedName("usedPointOfContact") val usedPointOfContact: UsedPointOfContactResponse? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("usedDateTimeUtc") val usedDateTimeUtc: DateTime? = null,
    @SerializedName("issuedPointOfContact") val issuedPointOfContact: IssuedPointOfContactResponse? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("issuedDateTimeUtc") val issuedDateTimeUtc: DateTime? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("blockedDateTimeUtc") val blockedDateTimeUtc: DateTime? = null
) {

    override fun toString() = "PromoCodeResponse(issueStatus=$issueStatus, ids=$ids, pool=$pool, " +
            "availableFromDateTimeUtc=$availableFromDateTimeUtc, " +
            "availableTillDateTimeUtc=$availableTillDateTimeUtc, isUsed=$isUsed, " +
            "usedPointOfContact=$usedPointOfContact, usedDateTimeUtc=$usedDateTimeUtc, " +
            "issuedPointOfContact=$issuedPointOfContact, issuedDateTimeUtc=$issuedDateTimeUtc, " +
            "blockedDateTimeUtc=$blockedDateTimeUtc)"

}
