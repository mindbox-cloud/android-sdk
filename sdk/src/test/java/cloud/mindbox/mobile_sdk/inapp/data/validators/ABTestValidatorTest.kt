package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.models.operation.response.ABTestDto
import cloud.mindbox.mobile_sdk.models.operation.response.SdkVersion
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class ABTestValidatorTest(
    private val abTestDto: ABTestDto,
    private val isValid: Boolean,
) {

    private val sdkVersionValidator = SdkVersionValidator()
    private val validator = ABTestValidator(sdkVersionValidator)

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: abtest({0}) is valid {1}")
        fun data(): Iterable<Array<Any?>> {
            val modulus = ABTestDto.VariantDto.ModulusDto(lower = 0, upper = 100)
            val abObject = ABTestDto.VariantDto.ObjectsDto(
                type = "inapps",
                kind = "all",
                inapps = listOf()
            )
            val variant1 = ABTestDto.VariantDto(
                id = "1",
                modulus = modulus.copy(upper = 50),
                objects = listOf(abObject)
            )
            val variant2 = ABTestDto.VariantDto(
                id = "2",
                modulus = modulus.copy(lower = 50),
                objects = listOf(abObject.copy(kind = "concrete"))
            )

            val abtest = ABTestDto(
                id = "123",
                sdkVersion = SdkVersion(null, null),
                salt = "salt",
                variants = listOf(variant1, variant2)
            )

            return listOf(
                abtest to true,
                abtest.copy(id = "sdfkj-sdfds-213123-dsew") to true,
                abtest.copy(salt = "sdfsdf-sdfsd") to true,
                abtest.copy(
                    variants = listOf(
                        variant1.copy(modulus = modulus.copy(upper = 1)),
                        variant1.copy(modulus = modulus.copy(lower = 1, upper = 50)),
                        variant2,
                    )
                ) to true,
                abtest.copy(
                    variants = listOf(
                        variant2.copy(modulus = modulus.copy(lower = 50, upper = 99)),
                        variant1
                    )
                ) to true,
                abtest.copy(variants = listOf()) to false,
                abtest.copy(variants = listOf(variant1)) to false,
                abtest.copy(variants = listOf(variant1, variant1)) to false,
                abtest.copy(variants = listOf(variant1, variant1, variant2)) to false,
                abtest.copy(
                    variants = listOf(
                        variant1.copy(modulus = modulus.copy(lower = -1, upper = 50)),
                        variant2
                    )
                ) to false,
                abtest.copy(
                    variants = listOf(
                        variant1,
                        variant2.copy(modulus = modulus.copy(upper = 101)),
                    )
                ) to false,
                abtest.copy(
                    variants = listOf(
                        variant1.copy(modulus = modulus.copy(lower = 1, upper = 50)),
                        variant2
                    )
                ) to false,
                abtest.copy(
                    variants = listOf(
                        variant2.copy(modulus = modulus.copy(lower = 50, upper = 98)),
                        variant1
                    )
                ) to false,
                abtest.copy(
                    variants = listOf(
                        variant2.copy(modulus = modulus.copy(lower = 60, upper = 100)),
                        variant1
                    )
                ) to false,
                abtest.copy(
                    variants = listOf(
                        variant2.copy(modulus = modulus.copy(lower = 40, upper = 100)),
                        variant1
                    )
                ) to false,
                abtest.copy(
                    variants = listOf(
                        variant1.copy(modulus = modulus.copy(lower = 0, upper = 50)),
                        variant1.copy(modulus = modulus.copy(lower = 50, upper = 50)),
                        variant2.copy(modulus = modulus.copy(lower = 50, upper = 100)),
                    )
                ) to false,
                abtest.copy(
                    variants = listOf(
                        variant1.copy(modulus = modulus.copy(lower = 0, upper = 60)),
                        variant1.copy(modulus = modulus.copy(lower = 40, upper = 60)),
                        variant2.copy(modulus = modulus.copy(lower = 60, upper = 100)),
                    )
                ) to false,
                abtest.copy(
                    variants = listOf(
                        variant1.copy(modulus = modulus.copy(lower = 0, upper = 60)),
                        variant1.copy(modulus = modulus.copy(lower = 40, upper = 100)),
                        variant2.copy(modulus = modulus.copy(lower = 60, upper = 40)),
                    )
                ) to false,
                abtest.copy(salt = "") to false,
                abtest.copy(salt = "") to false,
                abtest.copy(id = "") to false,
                abtest.copy(sdkVersion = null) to false,
            ).map { arrayOf(it.first, it.second) }
        }
    }

    @Test
    fun `abtest is valid`() {
        Assert.assertEquals(isValid, validator.isValid(abTestDto))
    }
}
