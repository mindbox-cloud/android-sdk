package cloud.mindbox.mobile_sdk.models.operation.response

import cloud.mindbox.mobile_sdk.models.operation.DateTime
import cloud.mindbox.mobile_sdk.models.operation.Ids
import cloud.mindbox.mobile_sdk.models.operation.adapters.DateTimeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

public open class PromoCodeResponse(
    @SerializedName("issueStatus") public val issueStatus: IssueStatusResponse? = null,
    @SerializedName("ids") public val ids: Ids? = null,
    @SerializedName("pool") public val pool: PoolResponse? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("availableFromDateTimeUtc") public val availableFromDateTimeUtc: DateTime? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("availableTillDateTimeUtc") public val availableTillDateTimeUtc: DateTime? = null,
    @SerializedName("isUsed") public val isUsed: Boolean? = null,
    @SerializedName("usedPointOfContact") public val usedPointOfContact: UsedPointOfContactResponse? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("usedDateTimeUtc") public val usedDateTimeUtc: DateTime? = null,
    @SerializedName("issuedPointOfContact") public val issuedPointOfContact: IssuedPointOfContactResponse? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("issuedDateTimeUtc") public val issuedDateTimeUtc: DateTime? = null,
    @JsonAdapter(DateTimeAdapter::class)
    @SerializedName("blockedDateTimeUtc") public val blockedDateTimeUtc: DateTime? = null
) {

    override fun toString(): String = "PromoCodeResponse(issueStatus=$issueStatus, ids=$ids, pool=$pool, " +
        "availableFromDateTimeUtc=$availableFromDateTimeUtc, " +
        "availableTillDateTimeUtc=$availableTillDateTimeUtc, isUsed=$isUsed, " +
        "usedPointOfContact=$usedPointOfContact, usedDateTimeUtc=$usedDateTimeUtc, " +
        "issuedPointOfContact=$issuedPointOfContact, issuedDateTimeUtc=$issuedDateTimeUtc, " +
        "blockedDateTimeUtc=$blockedDateTimeUtc)"
}
