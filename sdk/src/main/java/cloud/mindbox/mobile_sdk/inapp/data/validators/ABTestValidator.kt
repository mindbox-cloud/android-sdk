package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.models.operation.response.ABTestDto

internal class ABTestValidator(private val sdkVersionValidator: SdkVersionValidator) :
    Validator<ABTestDto?> {

    private val variantsValidator by lazy { VariantValidator() }

    override fun isValid(item: ABTestDto?): Boolean {
        if (item == null) {
            mindboxLogW("abtest can not be null")
            return false
        }

        if (item.id.isBlank()) {
            mindboxLogW("id can not be empty")
            return false
        }

        if (item.sdkVersion == null || !sdkVersionValidator.isValid(item.sdkVersion)) {
            mindboxLogW("sdkVersion is invalid")
            return false
        }

        if (item.salt.isNullOrBlank()) {
            mindboxLogW("salt can not be empty")
            return false
        }

        if (item.variants == null ||
            item.variants.size < 2
        ) {
            mindboxLogW("variants can not be empty")
            return false
        }

        if (item.variants.any { !variantsValidator.isValid(it) }) {
            mindboxLogW("Variant is invalid")
            return false
        }

        var start = 0
        item.variants.sortedBy { it.modulus!!.lower }
            .onEach { abtest ->
                if (abtest.modulus?.lower == start) {
                    start = abtest.modulus.upper!!
                } else {
                    mindboxLogW("Variants not have full cover")
                    return false
                }
            }

        if (start !in 99..100) {
            mindboxLogW("Variants not have full cover")
            return false
        }

        return true
    }
}