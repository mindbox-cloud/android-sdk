package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.equalsAny
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.models.operation.response.ABTestDto

internal class VariantValidator : Validator<ABTestDto.VariantDto?> {

    override fun isValid(item: ABTestDto.VariantDto?): Boolean {
        if (item == null) {
            mindboxLogW("Variant item can not be null")
            return false
        }

        if (item.modulus == null) {
            mindboxLogW("The 'modulus' field can not be null")
            return false
        }

        if (item.modulus.lower == null ||
            item.modulus.upper == null ||
            item.modulus.lower < 0 ||
            item.modulus.upper > 100 ||
            item.modulus.lower >= item.modulus.upper
        ) {
            mindboxLogW("The 'lower' and 'upper' field is invalid")
            return false
        }

        if (item.objects == null) {
            mindboxLogW("The 'objects' field can not be null")
            return false
        }

        if (item.objects.size != 1) {
            mindboxLogW("The 'objects' field must be only one")
            return false
        }

        if (!item.objects.first().type.equals(TYPE_IN_APPS)) {
            mindboxLogW("The 'objects' field type can be $TYPE_IN_APPS")
            return false
        }

        if (!item.objects.first().kind.equalsAny(ALL, CONCRETE)) {
            mindboxLogW("The 'kind' field must be $ALL or $CONCRETE")
            return false
        }

        return true
    }

    companion object {
        private const val TYPE_IN_APPS = "inapps"
        private const val ALL = "all"
        private const val CONCRETE = "concrete"
    }
}