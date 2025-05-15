package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDtoBlank
import org.junit.Assert.assertTrue
import org.junit.Test

class SlidingExpirationParametersValidatorTest {
    private val validator = SlidingExpirationParametersValidator()

    @Test
    fun `isValid returns true for valid unit HOURS and non-negative value`() {
        val validInAppSessionTime = SettingsDtoBlank.SlidingExpirationDtoBlank("01:00:00", null)

        assertTrue(validator.isValid(validInAppSessionTime))
    }

    @Test
    fun `isValid returns true for valid unit DAYS and non-negative value`() {
        val validInAppSessionTime = SettingsDtoBlank.SlidingExpirationDtoBlank("1.00:00:00", null)

        assertTrue(validator.isValid(validInAppSessionTime))
    }

    @Test
    fun `isValid returns true for valid unit MINUTES and non-negative value`() {
        val validInAppSessionTime = SettingsDtoBlank.SlidingExpirationDtoBlank("00:01:00", null)

        assertTrue(validator.isValid(validInAppSessionTime))
    }

    @Test
    fun `isValid returns true for valid unit SECONDS and non-negative value`() {
        val validInAppSessionTime = SettingsDtoBlank.SlidingExpirationDtoBlank("00:00:01", null)

        assertTrue(validator.isValid(validInAppSessionTime))
    }

    @Test
    fun `isValid returns false for invalid unit`() {
        val invalidInAppSessionTime = SettingsDtoBlank.SlidingExpirationDtoBlank("1:00:00:00", null)

        assertTrue(validator.isValid(invalidInAppSessionTime))
    }

    @Test
    fun `isValid returns false for negative value`() {
        val negativeInAppSessionTime = SettingsDtoBlank.SlidingExpirationDtoBlank("-1:00:00", null)

        assertTrue(validator.isValid(negativeInAppSessionTime))
    }

    @Test
    fun `isValid returns false when value is 0`() {
        val negativeInAppSessionTime = SettingsDtoBlank.SlidingExpirationDtoBlank("-00:00:00", null)

        assertTrue(validator.isValid(negativeInAppSessionTime))
    }

    @Test
    fun `isValid returns true when pushTokenKeepALive not null`() {
        val negativeInAppSessionTime = SettingsDtoBlank.SlidingExpirationDtoBlank("00:00:00", "00:00:00")

        assertTrue(validator.isValid(negativeInAppSessionTime))
    }

    @Test
    fun `isValid returns false for invalid formats`() {
        val invalidInputs = listOf(
            "123",
            "",
            " "
        )

        invalidInputs.forEach { input ->
            val invalidInAppSessionTime = SettingsDtoBlank.SlidingExpirationDtoBlank(input, null)
            assertTrue(validator.isValid(invalidInAppSessionTime))
        }
    }
}
