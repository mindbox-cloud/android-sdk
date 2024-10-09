package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.operation.response.FrequencyDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FrequencyValidatorTest {

    private lateinit var frequencyValidator: FrequencyValidator

    @Before
    fun setUp() {
        frequencyValidator = FrequencyValidator()
    }

    @Test
    fun `isValid returns true for FrequencyOnceDto with valid kind`() {
        val frequencyOnceDto =
            InAppStub.getFrequencyOnceDto().copy(type = "once", kind = "lifetime")

        val result = frequencyValidator.isValid(frequencyOnceDto)

        assertTrue(result)
    }

    @Test
    fun `isValid returns false for FrequencyOnceDto with invalid kind`() {
        val frequencyOnceDto =
            InAppStub.getFrequencyOnceDto().copy(type = "once", kind = "invalid_kind")

        val result = frequencyValidator.isValid(frequencyOnceDto)

        assertFalse(result)
    }

    @Test
    fun `isValid returns true for FrequencyPeriodicDto with valid unit and value`() {
        val frequencyPeriodicDto = InAppStub.getFrequencyPeriodicDto().copy(
            type = "periodic",
            unit = FrequencyDto.FrequencyPeriodicDto.FREQUENCY_UNIT_DAYS,
            value = 5
        )

        val result = frequencyValidator.isValid(frequencyPeriodicDto)

        assertTrue(result)
    }

    @Test
    fun `isValid returns false for FrequencyPeriodicDto with invalid unit`() {
        val frequencyPeriodicDto = InAppStub.getFrequencyPeriodicDto().copy(
            type = "periodic",
            unit = "invalid_unit",
            value = 5
        )

        val result = frequencyValidator.isValid(frequencyPeriodicDto)

        assertFalse(result)
    }

    @Test
    fun `isValid returns false for FrequencyPeriodicDto with negative value`() {
        val frequencyPeriodicDto = InAppStub.getFrequencyPeriodicDto().copy(
            type = "periodic",
            unit = FrequencyDto.FrequencyPeriodicDto.FREQUENCY_UNIT_DAYS,
            value = -5
        )

        val result = frequencyValidator.isValid(frequencyPeriodicDto)

        assertFalse(result)
    }
}
