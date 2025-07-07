package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

public open class BalanceResponse(
    @SerializedName("total") public val total: Double? = null,
    @SerializedName("available") public val available: Double? = null,
    @SerializedName("blocked") public val blocked: Double? = null,
    @SerializedName("nearestExpiration") public val nearestExpiration: NearestExpirationResponse? = null,
    @SerializedName("systemName") public val systemName: String? = null,
    @SerializedName("balanceType") public val balanceType: BalanceTypeResponse? = null
) {

    override fun toString(): String =
        "BalanceResponse(total=$total, available=$available, blocked=$blocked, " +
            "nearestExpiration=$nearestExpiration, systemName=$systemName, " +
            "balanceType=$balanceType)"
}
