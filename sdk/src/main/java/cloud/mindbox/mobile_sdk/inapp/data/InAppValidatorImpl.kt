package cloud.mindbox.mobile_sdk.inapp.data

import cloud.mindbox.mobile_sdk.inapp.domain.InAppValidator
import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import cloud.mindbox.mobile_sdk.models.operation.response.InAppDto
import cloud.mindbox.mobile_sdk.models.operation.response.PayloadDto

internal class InAppValidatorImpl : InAppValidator {

    private fun validateInAppTargeting(targeting: TreeTargetingDto?): Boolean {
        return when (targeting) {
            null -> {
                false
            }
            is TreeTargetingDto.UnionNodeDto -> {
                if (targeting.nodes.isNullOrEmpty()) return false
                var isValid = true
                for (internalTargeting in targeting.nodes) {
                    if (!validateInAppTargeting(internalTargeting)) {
                        isValid = false
                    }
                }
                isValid
            }
            is TreeTargetingDto.IntersectionNodeDto -> {
                if (targeting.nodes.isNullOrEmpty()) return false
                var isValid = true
                for (internalTargeting in targeting.nodes) {
                    if (!validateInAppTargeting(internalTargeting)) {
                        isValid = false
                    }
                }
                isValid
            }
            is TreeTargetingDto.SegmentNodeDto -> {
                targeting.segment_external_id != null
                        && targeting.segmentationInternalId != null
                        && (targeting.kind.equals(POSITIVE) || targeting.kind.equals(NEGATIVE))
                        && targeting.segmentationExternalId != null
                        && targeting.type != null
            }
            is TreeTargetingDto.TrueNodeDto -> {
                targeting.type != null
            }
            is TreeTargetingDto.CityNodeDto -> {
                targeting.type != null
                        && targeting.ids.isNullOrEmpty().not()
                        && (targeting.kind.equals(POSITIVE) || targeting.kind.equals(NEGATIVE))
            }
            is TreeTargetingDto.CountryNodeDto -> {
                targeting.type != null
                        && targeting.ids.isNullOrEmpty().not()
                        && (targeting.kind.equals(POSITIVE) || targeting.kind.equals(NEGATIVE))
            }
            is TreeTargetingDto.RegionNodeDto -> {
                targeting.type != null
                        && targeting.ids.isNullOrEmpty().not()
                        && (targeting.kind.equals(POSITIVE) || targeting.kind.equals(NEGATIVE))
            }
        }
    }

    private fun validateFormDto(inApp: InAppDto): Boolean {
        if (inApp.form?.variants.isNullOrEmpty()) return false
        var isValid = true
        inApp.form?.variants?.iterator()?.forEach { payloadDto ->
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

    companion object {
        /**
         * KIND VALUES
         * **/
        private const val POSITIVE = "positive"
        private const val NEGATIVE = "negative"
    }
}