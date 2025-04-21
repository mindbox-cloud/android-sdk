package cloud.mindbox.mobile_sdk.models

import cloud.mindbox.mobile_sdk.inapp.data.dto.GeoTargetingDto
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoTargeting

class GeoTargetingStub {
    companion object {
        fun getGeoTargeting() = GeoTargeting(cityId = "", regionId = "", countryId = "")

        fun getGeoTargetingDto() = GeoTargetingDto(cityId = "", regionId = "", countryId = "")
    }
}
