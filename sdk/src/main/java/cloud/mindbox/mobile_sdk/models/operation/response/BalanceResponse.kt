package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

open class BalanceResponse(
    @SerializedName("total") val total: Double? = null,
    @SerializedName("available") val available: Double? = null,
    @SerializedName("blocked") val blocked: Double? = null,
    @SerializedName("nearestExpiration") val nearestExpiration: NearestExpirationResponse? = null,
    @SerializedName("systemName") val systemName: String? = null,
    @SerializedName("balanceType") val balanceType: BalanceTypeResponse? = null
) {

    override fun toString() =
        "BalanceResponse(total=$total, available=$available, blocked=$blocked, " +
                "nearestExpiration=$nearestExpiration, systemName=$systemName, " +
                "balanceType=$balanceType)"

}