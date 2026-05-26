package cloud.mindbox.mobile_sdk.inapp.domain.models

import com.google.gson.annotations.SerializedName

internal data class GeoTargeting(
    @SerializedName("cityId") val cityId: String,
    @SerializedName("regionId") val regionId: String,
    @SerializedName("countryId") val countryId: String,
)
