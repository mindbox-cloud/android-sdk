package cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers

import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import cloud.mindbox.mobile_sdk.models.operation.response.FormDto
import cloud.mindbox.mobile_sdk.models.operation.response.FrequencyDto
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponseBlank
import com.google.gson.JsonObject

internal interface MobileConfigSerializationManager {

    fun deserializeToFrequencyDto(frequencyString: JsonObject?): FrequencyDto?

    fun deserializeToConfigDtoBlank(inAppConfig: String): InAppConfigResponseBlank?

    fun deserializeToInAppFormDto(inAppForm: JsonObject?): FormDto?

    fun deserializeToInAppTargetingDto(inAppTreeTargeting: JsonObject?): TreeTargetingDto?
}