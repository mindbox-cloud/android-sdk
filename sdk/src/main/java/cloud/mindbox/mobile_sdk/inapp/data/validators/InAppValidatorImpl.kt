package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.equalsAny
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.validators.InAppValidator
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponseBlank
import cloud.mindbox.mobile_sdk.models.operation.response.InAppDto

internal class InAppValidatorImpl(
    private val sdkVersionValidator: SdkVersionValidator,
    private val modalWindowValidator: ModalWindowValidator,
    private val snackbarValidator: SnackbarValidator,
    private val frequencyValidator: FrequencyValidator
) : InAppValidator {

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
                val rez = targeting.segmentExternalId != null &&
                    targeting.segmentationInternalId != null &&
                    targeting.kind.equalsAny(POSITIVE, NEGATIVE) &&
                    targeting.segmentationExternalId != null &&
                    targeting.type != null
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
                targeting.type != null &&
                    !targeting.ids.isNullOrEmpty() &&
                    targeting.kind.equalsAny(POSITIVE, NEGATIVE)
            }
            is TreeTargetingDto.CountryNodeDto -> {
                targeting.type != null &&
                    !targeting.ids.isNullOrEmpty() &&
                    targeting.kind.equalsAny(POSITIVE, NEGATIVE)
            }
            is TreeTargetingDto.RegionNodeDto -> {
                targeting.type != null &&
                    !targeting.ids.isNullOrEmpty() &&
                    targeting.kind.equalsAny(POSITIVE, NEGATIVE)
            }
            is TreeTargetingDto.OperationNodeDto -> {
                !targeting.type.isNullOrEmpty() &&
                    !targeting.systemName.isNullOrEmpty()
            }
            is TreeTargetingDto.ViewProductCategoryNodeDto -> {
                !targeting.type.isNullOrBlank() &&
                    targeting.kind.equalsAny(
                        SUBSTRING,
                        NOT_SUBSTRING,
                        STARTS_WITH,
                        ENDS_WITH
                    ) &&
                    !targeting.value.isNullOrBlank()
            }
            is TreeTargetingDto.ViewProductCategoryInNodeDto -> {
                !targeting.type.isNullOrBlank() &&
                    targeting.kind.equalsAny(ANY, NONE) &&
                    !targeting.values.isNullOrEmpty() &&
                    targeting.values.all { value ->
                        !value.id.isNullOrBlank() &&
                            !value.externalId.isNullOrBlank() &&
                            !value.externalSystemName.isNullOrBlank()
                    }
            }
            is TreeTargetingDto.ViewProductSegmentNodeDto -> {
                !targeting.type.isNullOrBlank() &&
                    targeting.kind.equalsAny(POSITIVE, NEGATIVE) &&
                    !targeting.segmentationExternalId.isNullOrBlank() &&
                    !targeting.segmentExternalId.isNullOrBlank() &&
                    !targeting.segmentationInternalId.isNullOrBlank()
            }
            is TreeTargetingDto.ViewProductNodeDto -> {
                !targeting.type.isNullOrBlank() &&
                    targeting.kind.equalsAny(
                        SUBSTRING,
                        NOT_SUBSTRING,
                        STARTS_WITH,
                        ENDS_WITH
                    ) &&
                    !targeting.value.isNullOrBlank()
            }

            is TreeTargetingDto.VisitNodeDto -> {
                !targeting.type.isNullOrBlank() &&
                    targeting.kind.equalsAny(
                        GREATER_OR_EQUALS, LOWER_OR_EQUALS, EQUALS, NOT_EQUALS
                    ) &&
                    (targeting.value?.let { it > 0 } == true)
            }

            is TreeTargetingDto.PushPermissionDto -> {
                !targeting.type.isNullOrBlank() && targeting.value != null
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

                (payloadDto is PayloadDto.ModalWindowDto) -> {
                    mindboxLogD("Start checking modal window payload of inApp with id = ${inApp.id}")
                    isValid = modalWindowValidator.isValid(payloadDto)
                    mindboxLogD("Finish checking modal window inApp with id = ${inApp.id}. InApp is valid = $isValid")
                }
                (payloadDto is PayloadDto.SnackbarDto) -> {
                    mindboxLogD("Start checking snackbar payload of inApp with id = ${inApp.id}")
                    isValid = snackbarValidator.isValid(payloadDto)
                    mindboxLogD("Finish checking snackbar inApp with id = ${inApp.id}. InApp is valid = $isValid")
                }
            }
        }
        return isValid
    }

    override fun validateInAppVersion(inAppDto: InAppConfigResponseBlank.InAppDtoBlank): Boolean {
        return sdkVersionValidator.isValid(inAppDto.sdkVersion)
    }

    override fun validateInApp(inApp: InAppDto): Boolean {
        return validateInAppTargeting(inApp.id, inApp.targeting) && validateFormDto(inApp) && validateFrequency(inApp)
    }

    private fun validateFrequency(inApp: InAppDto): Boolean {
        mindboxLogI("Start checking frequency of inapp with id = ${inApp.id}")
        val isValid = frequencyValidator.isValid(inApp.frequency)
        mindboxLogI("Finish checking frequency of inapp with id = ${inApp.id}")
        return isValid
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

        private const val GREATER_OR_EQUALS = "gte"
        private const val LOWER_OR_EQUALS = "lte"
        private const val EQUALS = "equals"
        private const val NOT_EQUALS = "notEquals"
    }
}
