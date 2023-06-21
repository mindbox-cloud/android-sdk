package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.models.operation.response.ABTestDto
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class VariantValidatorTest(
    private val variant: ABTestDto.VariantDto?,
    private val isValid: Boolean,
) {

    private val variantValidator = VariantValidator()

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: variant({0}) is valid {1}")
        fun data(): Iterable<Array<Any?>> {
            val objectDto = ABTestDto.VariantDto.ObjectsDto(
                type = "inapps",
                kind = "all",
                inapps = listOf()
            )
            val variant = ABTestDto.VariantDto(
                id = "dsa",
                modulus = ABTestDto.VariantDto.ModulusDto(
                    lower = 0,
                    upper = 100
                ),
                objects = listOf(objectDto)
            )
            return listOf(
                variant to true,
                variant.copy(modulus = variant.modulus!!.copy(lower = 99)) to true,
                variant.copy(modulus = variant.modulus.copy(lower = 42, upper = 43)) to true,
                variant.copy(modulus = variant.modulus.copy(upper = 1)) to true,
                variant.copy(objects = listOf(objectDto.copy(kind = "concrete"))) to true,
                variant.copy(objects = listOf(objectDto.copy(inapps = null))) to true,
                variant.copy(objects = listOf(objectDto.copy(inapps = listOf("")))) to true,
                variant.copy(objects = listOf(objectDto.copy(inapps = listOf("123", "321")))) to true,
                variant.copy(objects = listOf(objectDto.copy(inapps = listOf("123")))) to true,

                null to false,
                variant.copy(id = "") to false,
                variant.copy(id = "   ") to false,
                variant.copy(modulus = null) to false,
                variant.copy(objects = null) to false,
                variant.copy(modulus = variant.modulus.copy(lower = -100)) to false,
                variant.copy(modulus = variant.modulus.copy(lower = 150)) to false,
                variant.copy(modulus = variant.modulus.copy(upper = 1000)) to false,
                variant.copy(modulus = variant.modulus.copy(upper = -1)) to false,
                variant.copy(modulus = variant.modulus.copy(upper = 50, lower = 50)) to false,
                variant.copy(modulus = variant.modulus.copy(upper = 0, lower = 100)) to false,
                variant.copy(objects = listOf(objectDto.copy(type = "sdf"))) to false,
                variant.copy(objects = listOf(objectDto.copy(type = ""))) to false,
                variant.copy(objects = listOf(objectDto.copy(type = null))) to false,
                variant.copy(objects = listOf(objectDto.copy(kind = "ALL)"))) to false,
                variant.copy(objects = listOf(objectDto.copy(kind = "All)"))) to false,
                variant.copy(objects = listOf(objectDto.copy(kind = ""))) to false,
                variant.copy(objects = listOf(objectDto.copy(kind = "null)"))) to false,
                variant.copy(objects = listOf(objectDto.copy(kind = null))) to false,
                variant.copy(objects = listOf(objectDto, objectDto)) to false,
                variant.copy(objects = listOf()) to false,
                variant.copy(objects = null) to false,
            ).map { arrayOf(it.first, it.second) }
        }
    }

    @Test
    fun `variant is valid`() {
        assertEquals(isValid, variantValidator.isValid(variant))
    }
}