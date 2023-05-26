package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.equalsAny
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.validators.InAppValidator
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponseBlank
import cloud.mindbox.mobile_sdk.models.operation.response.InAppDto
import cloud.mindbox.mobile_sdk.models.operation.response.PayloadDto
import cloud.mindbox.mobile_sdk.utils.Constants

internal class InAppValidatorImpl : InAppValidator {

    private fun validateInAppTargeting(id: String, targeting: TreeTargetingDto?): Boolean {
        return when (targeting) {
            null -> {
                MindboxLoggerImpl.d(
                    this,
                    "targeting is null for in-app with $id"
                )
                false
            }
            is TreeTargetingDto.UnionNodeDto -> {

                if (targeting.nodes.isNullOrEmpty()) {
                    MindboxLoggerImpl.d(
                        this,
                        "nodes is ${targeting.nodes.toString()} for in-app with id $id"
                    )
                    return false
                }
                var isValid = true
                for (internalTargeting in targeting.nodes) {
                    if (!validateInAppTargeting(id, internalTargeting)) {
                        isValid = false
                    }
                }
                isValid
            }
            is TreeTargetingDto.IntersectionNodeDto -> {
                if (targeting.nodes.isNullOrEmpty()) {
                    MindboxLoggerImpl.d(
                        this,
                        "nodes is ${targeting.nodes.toString()} for in-app with id $id"
                    )
                    return false
                }
                var isValid = true
                for (internalTargeting in targeting.nodes) {
                    if (!validateInAppTargeting(id, internalTargeting)) {
                        isValid = false
                    }
                }
                isValid
            }
            is TreeTargetingDto.SegmentNodeDto -> {
                val rez = targeting.segmentExternalId != null
                        && targeting.segmentationInternalId != null
                        && targeting.kind.equalsAny(POSITIVE, NEGATIVE)
                        && targeting.segmentationExternalId != null
                        && targeting.type != null
                if (!rez) {
                    MindboxLoggerImpl.d(
                        this,
                        "some segment properties are corrupted"
                    )
                }
                rez
            }
            is TreeTargetingDto.TrueNodeDto -> {
                targeting.type != null
            }
            is TreeTargetingDto.CityNodeDto -> {
                targeting.type != null
                        && !targeting.ids.isNullOrEmpty()
                        && targeting.kind.equalsAny(POSITIVE, NEGATIVE)
            }
            is TreeTargetingDto.CountryNodeDto -> {
                targeting.type != null
                        && !targeting.ids.isNullOrEmpty()
                        && targeting.kind.equalsAny(POSITIVE, NEGATIVE)
            }
            is TreeTargetingDto.RegionNodeDto -> {
                targeting.type != null
                        && !targeting.ids.isNullOrEmpty()
                        && targeting.kind.equalsAny(POSITIVE, NEGATIVE)
            }
            is TreeTargetingDto.OperationNodeDto -> {
                !targeting.type.isNullOrEmpty()
                        && !targeting.systemName.isNullOrEmpty()
            }
            is TreeTargetingDto.ViewProductCategoryNodeDto -> {
                !targeting.type.isNullOrBlank()
                        && targeting.kind.equalsAny(
                    SUBSTRING,
                    NOT_SUBSTRING,
                    STARTS_WITH,
                    ENDS_WITH
                )
                        && !targeting.value.isNullOrBlank()
            }
            is TreeTargetingDto.ViewProductCategoryInNodeDto -> {
                !targeting.type.isNullOrBlank()
                        && targeting.kind.equalsAny(ANY, NONE)
                        && !targeting.values.isNullOrEmpty()
                        && targeting.values.all { value ->
                    !value.id.isNullOrBlank()
                            && !value.externalId.isNullOrBlank()
                            && !value.externalSystemName.isNullOrBlank()
                }
            }
            is TreeTargetingDto.ViewProductSegmentNodeDto -> {
                !targeting.type.isNullOrBlank()
                        && targeting.kind.equalsAny(POSITIVE, NEGATIVE)
                        && !targeting.segmentationExternalId.isNullOrBlank()
                        && !targeting.segmentExternalId.isNullOrBlank()
                        && !targeting.segmentationInternalId.isNullOrBlank()
            }
            is TreeTargetingDto.ViewProductNodeDto -> {
                !targeting.type.isNullOrBlank() && targeting.kind.equalsAny(
                    SUBSTRING,
                    NOT_SUBSTRING,
                    STARTS_WITH,
                    ENDS_WITH
                ) && !targeting.value.isNullOrBlank()
            }
        }
    }

    private fun validateFormDto(inApp: InAppDto): Boolean {
        if (inApp.form?.variants.isNullOrEmpty()) return false
        var isValid = true
        inApp.form?.variants?.iterator()?.forEach { payloadDto ->
            when {
                (payloadDto == null) -> {
                    MindboxLoggerImpl.d(
                        this,
                        "payload is null for in-app with id ${inApp.id}"
                    )
                    isValid = false
                }
                (payloadDto is PayloadDto.SimpleImage) -> {
                    if ((payloadDto.type == null) or (payloadDto.imageUrl == null)) {
                        MindboxLoggerImpl.d(
                            this,
                            "some properties of in-app with id ${inApp.id} are null. type: ${payloadDto.type}, imageUrl: ${payloadDto.imageUrl}"
                        )
                        isValid = false
                    }

                }
            }
        }
        return isValid
    }

    override fun validateInAppVersion(inAppDto: InAppConfigResponseBlank.InAppDtoBlank): Boolean {
        val sdkVersion = inAppDto.sdkVersion ?: return false
        val minVersionValid = sdkVersion.minVersion?.let { min ->
            min <= Constants.SDK_VERSION_NUMERIC
        } ?: true
        val maxVersionValid = sdkVersion.maxVersion?.let { max ->
            max >= Constants.SDK_VERSION_NUMERIC
        } ?: true
        return minVersionValid && maxVersionValid
    }

    override fun validateInApp(inApp: InAppDto): Boolean {
        return validateInAppTargeting(inApp.id, inApp.targeting) and validateFormDto(inApp)
    }

    companion object {
        /**
         * KIND VALUES
         * **/
        private const val POSITIVE = "positive"
        private const val NEGATIVE = "negative"

        private const val ANY = "any"
        private const val NONE = "none"

        private const val SUBSTRING = "substring"
        private const val NOT_SUBSTRING = "notSubstring"
        private const val STARTS_WITH = "startsWith"
        private const val ENDS_WITH = "endsWith"
    }
}