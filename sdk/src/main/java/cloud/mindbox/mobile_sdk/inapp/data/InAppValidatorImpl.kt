package cloud.mindbox.mobile_sdk.inapp.data

import cloud.mindbox.mobile_sdk.inapp.domain.InAppValidator
import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import cloud.mindbox.mobile_sdk.models.operation.response.InAppDto
import cloud.mindbox.mobile_sdk.models.operation.response.PayloadDto

internal class InAppValidatorImpl : InAppValidator {

    private fun validateInAppTargeting(targeting: TreeTargetingDto?): Boolean {
        return when {
            (targeting == null) -> {
                false
            }
            (targeting is TreeTargetingDto.UnionNodeDto) -> {
                var isValid = false
                for (internalTargeting in targeting.nodes ?: return false) {
                    if (validateInAppTargeting(internalTargeting)) {
                        isValid = true
                    }
                }
                isValid
            }
            (targeting is TreeTargetingDto.IntersectionNodeDto) -> {
                var isValid = true
                for (internalTargeting in targeting.nodes ?: return false) {
                    if (!validateInAppTargeting(internalTargeting)) {
                        isValid = false
                    }
                }
                isValid
            }
            (targeting is TreeTargetingDto.SegmentNodeDto) -> {
                targeting.segment_external_id != null
                        && targeting.segmentationInternalId != null
                        && targeting.segmentationExternalId != null
                        && targeting.kind != null
                        && targeting.type != null
            }
            else -> {
                true
            }
        }
    }

    private fun validateFormDto(inApp: InAppDto): Boolean {
        var isValid = true
        inApp.form?.variants?.forEach { payloadDto ->
            when {
                (payloadDto == null) -> {
                    isValid = false
                }
                (payloadDto is PayloadDto.SimpleImage) -> {
                    if ((payloadDto.type == null) or (payloadDto.imageUrl == null))
                        isValid = false
                }
            }
        }
        return isValid
    }

    override fun validateInApp(inApp: InAppDto): Boolean {
        return validateInAppTargeting(inApp.targeting) and validateFormDto(inApp)
    }
}