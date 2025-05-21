package cloud.mindbox.mobile_sdk.inapp.data.dto

import com.google.gson.annotations.SerializedName

internal data class GeoTargetingDto(
    @SerializedName("city_id")
    val cityId: String?,
    @SerializedName("region_id")
    val regionId: String?,
    @SerializedName("country_id")
    val countryId: String?,
)
