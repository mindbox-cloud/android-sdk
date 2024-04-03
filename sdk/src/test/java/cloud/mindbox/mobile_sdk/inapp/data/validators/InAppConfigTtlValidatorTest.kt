package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTtl
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTtlData
import cloud.mindbox.mobile_sdk.models.operation.response.TtlDto
import cloud.mindbox.mobile_sdk.models.operation.response.TtlParametersDto
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InAppConfigTtlValidatorTest {

   private companion object TestValue {
        const val SECONDS_29 = 29 * 1000
        const val SECONDS_31 = 31 * 1000
        const val SECONDS_59 = 59 * 1000
        const val MINUTES_59 = 59 * 60 * 1000
        const val MINUTES_1_SECOND_1 = 61 * 1000
        const val MINUTES_61 = 61 * 60 * 1000
        const val HOURS_23_MINUTES_59 = ((23 * 60) + 59) * 60 * 1000
        const val HOURS_24_SECONDS_1 = ((24 * 60 * 60) + 1) * 1000
    }

    @Before
    fun setUp() {
        mockkObject(MindboxPreferences)
    }

    @After
    fun tearDown() {
        unmockkObject(MindboxPreferences)
    }

    @Test
    fun `isValid returns true when TTL has not expired and unit is HOURS`() {

        val mockTtl = TtlDto(TtlParametersDto(unit = InAppTtl.fromString("HOURS")!!, value = 1L))
        val currentTime = System.currentTimeMillis()
        val inAppTtlData = InAppTtlData(shouldCheckInAppTtl = true, ttl = mockTtl)
        every { MindboxPreferences.inAppConfigUpdatedTime } returns currentTime - MINUTES_59
        val validator = InAppConfigTtlValidator()

        val isValid = validator.isValid(inAppTtlData)

        assertTrue(isValid)
    }

    @Test
    fun `isValid returns false when TTL has expired and unit is HOURS`() {

        val mockTtl = TtlDto(TtlParametersDto(unit = InAppTtl.fromString("HOURS")!!, value = 1L))
        val currentTime = System.currentTimeMillis()
        val inAppTtlData = InAppTtlData(shouldCheckInAppTtl = true, ttl = mockTtl)
        every { MindboxPreferences.inAppConfigUpdatedTime } returns currentTime - MINUTES_61
        val validator = InAppConfigTtlValidator()

        val isValid = validator.isValid(inAppTtlData)

        assertFalse(isValid)
    }

    @Test
    fun `isValid returns true when TTL has not expired and unit is DAYS`() {

        val mockTtl = TtlDto(TtlParametersDto(unit = InAppTtl.fromString("DAYS")!!, value = 1L))
        val currentTime = System.currentTimeMillis()
        val inAppTtlData = InAppTtlData(shouldCheckInAppTtl = true, ttl = mockTtl)
        every { MindboxPreferences.inAppConfigUpdatedTime } returns currentTime - HOURS_23_MINUTES_59
        val validator = InAppConfigTtlValidator()

        val isValid = validator.isValid(inAppTtlData)

        assertTrue(isValid)
    }

    @Test
    fun `isValid returns false when TTL has expired and unit is DAYS`() {

        val mockTtl = TtlDto(TtlParametersDto(unit = InAppTtl.fromString("DAYS")!!, value = 1L))
        val currentTime = System.currentTimeMillis()
        val inAppTtlData = InAppTtlData(shouldCheckInAppTtl = true, ttl = mockTtl)
        every { MindboxPreferences.inAppConfigUpdatedTime } returns currentTime - HOURS_24_SECONDS_1
        val validator = InAppConfigTtlValidator()

        val isValid = validator.isValid(inAppTtlData)

        assertFalse(isValid)
    }

    @Test
    fun `isValid returns true when TTL has not expired and unit is MINUTES`() {

        val mockTtl = TtlDto(TtlParametersDto(unit = InAppTtl.fromString("MINUTES")!!, value = 1L))
        val currentTime = System.currentTimeMillis()
        val inAppTtlData = InAppTtlData(shouldCheckInAppTtl = true, ttl = mockTtl)
        every { MindboxPreferences.inAppConfigUpdatedTime } returns currentTime - SECONDS_59
        val validator = InAppConfigTtlValidator()

        val isValid = validator.isValid(inAppTtlData)

        assertTrue(isValid)
    }

    @Test
    fun `isValid returns false when TTL has expired and unit is MINUTES`() {

        val mockTtl = TtlDto(TtlParametersDto(unit = InAppTtl.fromString("MINUTES")!!, value = 1L))
        val currentTime = System.currentTimeMillis()
        val inAppTtlData = InAppTtlData(shouldCheckInAppTtl = true, ttl = mockTtl)
        every { MindboxPreferences.inAppConfigUpdatedTime } returns currentTime - MINUTES_1_SECOND_1
        val validator = InAppConfigTtlValidator()

        val isValid = validator.isValid(inAppTtlData)

        assertFalse(isValid)
    }

    @Test
    fun `isValid returns true when TTL has not expired and unit is SECONDS`() {

        val mockTtl = TtlDto(TtlParametersDto(unit = InAppTtl.fromString("SECONDS")!!, value = 30L))
        val currentTime = System.currentTimeMillis()
        val inAppTtlData = InAppTtlData(shouldCheckInAppTtl = true, ttl = mockTtl)
        every { MindboxPreferences.inAppConfigUpdatedTime } returns currentTime - SECONDS_29
        val validator = InAppConfigTtlValidator()

        val isValid = validator.isValid(inAppTtlData)

        assertTrue(isValid)
    }

    @Test
    fun `isValid returns false when TTL has not expired and unit is SECONDS`() {

        val mockTtl = TtlDto(TtlParametersDto(unit = InAppTtl.fromString("SECONDS")!!, value = 30L))
        val currentTime = System.currentTimeMillis()
        val inAppTtlData = InAppTtlData(shouldCheckInAppTtl = true, ttl = mockTtl)
        every { MindboxPreferences.inAppConfigUpdatedTime } returns currentTime - SECONDS_31
        val validator = InAppConfigTtlValidator()

        val isValid = validator.isValid(inAppTtlData)

        assertFalse(isValid)
    }

    @Test
    fun `isValid returns true when shouldCheckInAppTtl is false and ttl is null`() {

        val inAppTtlData = InAppTtlData(shouldCheckInAppTtl = false, ttl = null)
        val validator = InAppConfigTtlValidator()

        val isValid = validator.isValid(inAppTtlData)

        assertTrue(isValid)
    }

    @Test
    fun `isValid returns true when shouldCheckInAppTtl is false and ttl is not null`() {

        val currentTime = System.currentTimeMillis()
        val mockTtl = TtlDto(TtlParametersDto(unit = InAppTtl.fromString("SECONDS")!!, value = 1L))
        val inAppTtlData = InAppTtlData(shouldCheckInAppTtl = false, ttl = mockTtl)
        every { MindboxPreferences.inAppConfigUpdatedTime } returns currentTime
        val validator = InAppConfigTtlValidator()

        val isValid = validator.isValid(inAppTtlData)

        assertTrue(isValid)
    }

    @Test
    fun `isValid returns true when ttlParametersIsNull and shouldCheckInAppTtl is true`() {

        val inAppTtlData = InAppTtlData(shouldCheckInAppTtl = true, ttl = null)
        val validator = InAppConfigTtlValidator()

        val isValid = validator.isValid(inAppTtlData)

        assertTrue(isValid)
    }
}