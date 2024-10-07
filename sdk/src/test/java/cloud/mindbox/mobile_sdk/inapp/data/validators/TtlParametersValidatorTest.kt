package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDtoBlank
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtlParametersValidatorTest {
    private val validator = TtlParametersValidator()

    @Test
    fun `isValid returns true for valid unit HOURS and non-negative value`() {
        val validTtl = SettingsDtoBlank.TtlDtoBlank("01:00:00")

        assertTrue(validator.isValid(validTtl))
    }

    @Test
    fun `isValid returns true for valid unit DAYS and non-negative value`() {
        val validTtl = SettingsDtoBlank.TtlDtoBlank("1.00:00:00")

        assertTrue(validator.isValid(validTtl))
    }

    @Test
    fun `isValid returns true for valid unit MINUTES and non-negative value`() {
        val validTtl = SettingsDtoBlank.TtlDtoBlank("00:01:00")

        assertTrue(validator.isValid(validTtl))
    }

    @Test
    fun `isValid returns true for valid unit SECONDS and non-negative value`() {
        val validTtl = SettingsDtoBlank.TtlDtoBlank("00:00:01")

        assertTrue(validator.isValid(validTtl))
    }

    @Test
    fun `isValid returns false for invalid unit`() {
        val invalidUnitTtl = SettingsDtoBlank.TtlDtoBlank("1:00:00:00")

        assertFalse(validator.isValid(invalidUnitTtl))
    }

    @Test
    fun `isValid returns false for negative value`() {
        val negativeValueTtl = SettingsDtoBlank.TtlDtoBlank("-1:00:00")

        assertFalse(validator.isValid(negativeValueTtl))
    }

    @Test
    fun `isValid returns true when value is 0`() {
        val negativeValueTtl = SettingsDtoBlank.TtlDtoBlank("-00:00:00")

        assertTrue(validator.isValid(negativeValueTtl))
    }

    @Test
    fun `isValid returns false when ttl is valid string`() {
        val nullTtl = SettingsDtoBlank.TtlDtoBlank("one day")

        assertFalse(validator.isValid(nullTtl))
    }
}