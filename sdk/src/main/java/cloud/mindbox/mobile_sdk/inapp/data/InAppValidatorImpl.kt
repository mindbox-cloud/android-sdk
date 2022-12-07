package cloud.mindbox.mobile_sdk.inapp.data

import cloud.mindbox.mobile_sdk.inapp.domain.InAppValidator
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import cloud.mindbox.mobile_sdk.models.operation.response.InAppDto
import cloud.mindbox.mobile_sdk.models.operation.response.PayloadDto

internal class InAppValidatorImpl : InAppValidator {

    private fun validateInAppTargeting(id: String, targeting: TreeTargetingDto?): Boolean {
        return when (targeting) {
            null -> {
                MindboxLoggerImpl.d(InAppRepositoryImpl, "targeting is null for in-app with $id")
                false
            }
            is TreeTargetingDto.UnionNodeDto -> {

                if (targeting.nodes.isNullOrEmpty()) {
                    MindboxLoggerImpl.d(InAppRepositoryImpl,
                        "nodes is ${targeting.nodes.toString()} for in-app with id $id")
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
                    MindboxLoggerImpl.d(InAppRepositoryImpl,
                        "nodes is ${targeting.nodes.toString()} for in-app with id $id")
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
                val rez = targeting.segment_external_id != null
                        && targeting.segmentationInternalId != null
                        && (targeting.kind.equals(POSITIVE) || targeting.kind.equals(NEGATIVE))
                        && targeting.segmentationExternalId != null
                        && targeting.type != null
                if (!rez) {
                    MindboxLoggerImpl.d(InAppRepositoryImpl,
                        "some segment properties are corrupted")
                }
                rez
            }
            is TreeTargetingDto.TrueNodeDto -> {
                true
            }
        }
    }

    private fun validateFormDto(inApp: InAppDto): Boolean {
        if (inApp.form?.variants.isNullOrEmpty()) return false
        var isValid = true
        inApp.form?.variants?.iterator()?.forEach { payloadDto ->
            when {
                (payloadDto == null) -> {
                    MindboxLoggerImpl.d(InAppRepositoryImpl,
                        "payload is null for in-app with id ${inApp.id}")
                    isValid = false
                }
                (payloadDto is PayloadDto.SimpleImage) -> {
                    if ((payloadDto.type == null) or (payloadDto.imageUrl == null)) {
                        MindboxLoggerImpl.d(InAppRepositoryImpl,
                            "some properties of in-app with id ${inApp.id} are null. type: ${payloadDto.type}, imageUrl: ${payloadDto.imageUrl}")
                        isValid = false
                    }

                }
            }
        }
        return isValid
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
    }
}