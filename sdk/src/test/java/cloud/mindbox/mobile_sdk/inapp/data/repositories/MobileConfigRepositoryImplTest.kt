package cloud.mindbox.mobile_sdk.inapp.data.repositories

import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.models.TimeSpan
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponseBlank
import io.mockk.*
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import cloud.mindbox.mobile_sdk.inapp.data.validators.TimeSpanPositiveValidator
import cloud.mindbox.mobile_sdk.models.InAppStub

internal class MobileConfigRepositoryImplTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @RelaxedMockK
    private lateinit var inAppMapper: InAppMapper

    private lateinit var repository: MobileConfigRepositoryImpl

    @Before
    fun setUp() {
        repository = createRepository()
    }

    @Test
    fun `getInApps when delayTime is valid positive string then passes TimeSpan to mapper`() {
        val testDto = InAppStub.getInAppDtoBlank().copy(delayTime = "00:30:00")
        val configBlank = InAppConfigResponseBlank(listOf(testDto), null, null, null)

        repository.getInApps(configBlank)

        val slot = slot<TimeSpan>()
        verify(exactly = 1) { inAppMapper.mapToInAppDto(any(), capture(slot), any(), any(), any()) }
        assertEquals("00:30:00", slot.captured.value)
    }

    @Test
    fun `getInApps when delayTime is negative string then passes null to mapper`() {
        val testDto = InAppStub.getInAppDtoBlank().copy(delayTime = "-00:30:00")
        val configBlank = InAppConfigResponseBlank(listOf(testDto), null, null, null)

        repository.getInApps(configBlank)

        verify(exactly = 1) { inAppMapper.mapToInAppDto(any(), null, any(), any(), any()) }
    }

    @Test
    fun `getInApps when delayTime is zero string then passes null to mapper`() {
        val testDto = InAppStub.getInAppDtoBlank().copy(delayTime = "00:00:00")
        val configBlank = InAppConfigResponseBlank(listOf(testDto), null, null, null)

        repository.getInApps(configBlank)

        verify(exactly = 1) { inAppMapper.mapToInAppDto(any(), null, any(), any(), any()) }
    }

    @Test
    fun `getInApps when delayTime is null then passes null to mapper`() {
        val testDto = InAppStub.getInAppDtoBlank().copy(delayTime = null)
        val configBlank = InAppConfigResponseBlank(listOf(testDto), null, null, null)

        repository.getInApps(configBlank)

        verify(exactly = 1) { inAppMapper.mapToInAppDto(any(), null, any(), any(), any()) }
    }

    private fun createRepository(): MobileConfigRepositoryImpl {
        return MobileConfigRepositoryImpl(
            inAppMapper = inAppMapper,
            timeSpanPositiveValidator = TimeSpanPositiveValidator(),
            inAppConfigTtlValidator = mockk(relaxed = true) {
                every { isValid(any()) } returns true
            },
            inAppValidator = mockk(relaxed = true) {
                every { validateInAppVersion(any()) } returns true
                every { validateInApp(any()) } returns true
            },
            mobileConfigSerializationManager = mockk(relaxed = true) {
                every { deserializeToInAppTargetingDto(any()) } returns mockk()
            },
            monitoringValidator = mockk(relaxed = true),
            abTestValidator = mockk(relaxed = true),
            operationNameValidator = mockk(relaxed = true),
            operationValidator = mockk(relaxed = true),
            gatewayManager = mockk(relaxed = true),
            defaultDataManager = mockk(relaxed = true) {
                every { fillFormData(any()) } returns mockk()
                every { fillFrequencyData(any()) } returns mockk()
            },
            ttlParametersValidator = mockk(relaxed = true),
            sessionStorageManager = mockk(relaxed = true),
            mobileConfigSettingsManager = mockk(relaxed = true),
            integerPositiveValidator = mockk(relaxed = true),
            inappSettingsManager = mockk(relaxed = true)
        )
    }
}
