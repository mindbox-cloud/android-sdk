package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.models.operation.response.ABTestDto

internal class ABTestValidator(private val sdkVersionValidator: SdkVersionValidator) :
    Validator<ABTestDto?> {

    private val variantsValidator by lazy { VariantValidator() }

    override fun isValid(item: ABTestDto?): Boolean {
        if (item == null) {
            mindboxLogW("The element in abtests block cannot be null. All abtests will not be used.")
            return false
        }

        if (item.id.isBlank()) {
            mindboxLogW("The field 'id' in abtests block cannot be null. All abtests will not be used.")
            return false
        }

        if (item.sdkVersion == null || !sdkVersionValidator.isValid(item.sdkVersion)) {
            mindboxLogW("In abtest ${item.id} 'sdkVersion' field is invalid. All abtests will not be used.")
            return false
        }

        if (item.salt.isBlank()) {
            mindboxLogW("In abtest ${item.id} 'salt' field is invalid. All abtests will not be used.")
            return false
        }

        if (item.variants == null ||
            item.variants.size < 2
        ) {
            mindboxLogW("In abtest ${item.id} 'variants' field must have at least two items. All abtests will not be used.")
            return false
        }

        if (item.variants.any { !variantsValidator.isValid(it) }) {
            mindboxLogW("In abtest ${item.id} 'variants' field is invalid. All abtests will not be used.")
            return false
        }

        var start = 0
        item.variants.sortedBy { it.modulus!!.lower }
            .onEach { abtest ->
                if (abtest.modulus?.lower == start) {
                    start = abtest.modulus.upper!!
                } else {
                    mindboxLogW("In abtest ${item.id} 'variants' field not have full cover. All abtests will not be used.")
                    return false
                }
            }

        if (start !in 99..100) {
            mindboxLogW("In abtest ${item.id} 'variants' field not have full cover. All abtests will not be used.")
            return false
        }

        return true
    }
}