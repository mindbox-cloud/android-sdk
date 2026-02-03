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
import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDtoBlank

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

    @Test
    fun `getFeatureToggles when featureToggles is present with true returns FeatureTogglesDto with true`() {
        val settingsBlank = SettingsDtoBlank(
            operations = null,
            ttl = null,
            slidingExpiration = null,
            inappSettings = null,
            featureToggles = SettingsDtoBlank.FeatureTogglesDtoBlank(
                shouldSendInAppShowError = true
            )
        )
        val configBlank = InAppConfigResponseBlank(null, null, settingsBlank, null)

        val result = repository.getFeatureToggles(configBlank)

        assertNotNull(result)
        assertEquals(true, result?.shouldSendInAppShowError)
    }

    @Test
    fun `getFeatureToggles when featureToggles is present with false returns FeatureTogglesDto with false`() {
        val settingsBlank = SettingsDtoBlank(
            operations = null,
            ttl = null,
            slidingExpiration = null,
            inappSettings = null,
            featureToggles = SettingsDtoBlank.FeatureTogglesDtoBlank(
                shouldSendInAppShowError = false
            )
        )
        val configBlank = InAppConfigResponseBlank(null, null, settingsBlank, null)

        val result = repository.getFeatureToggles(configBlank)

        assertNotNull(result)
        assertEquals(false, result?.shouldSendInAppShowError)
    }

    @Test
    fun `getFeatureToggles when featureToggles is present with null value returns FeatureTogglesDto with false`() {
        val settingsBlank = SettingsDtoBlank(
            operations = null,
            ttl = null,
            slidingExpiration = null,
            inappSettings = null,
            featureToggles = SettingsDtoBlank.FeatureTogglesDtoBlank(
                shouldSendInAppShowError = null
            )
        )
        val configBlank = InAppConfigResponseBlank(null, null, settingsBlank, null)

        val result = repository.getFeatureToggles(configBlank)

        assertNotNull(result)
        assertEquals(false, result?.shouldSendInAppShowError)
    }

    @Test
    fun `getFeatureToggles when featureToggles section is absent returns null`() {
        val settingsBlank = SettingsDtoBlank(
            operations = null,
            ttl = null,
            slidingExpiration = null,
            inappSettings = null,
            featureToggles = null
        )
        val configBlank = InAppConfigResponseBlank(null, null, settingsBlank, null)

        val result = repository.getFeatureToggles(configBlank)

        assertNull(result)
    }

    @Test
    fun `getFeatureToggles when settings is null returns null`() {
        val configBlank = InAppConfigResponseBlank(null, null, null, null)

        val result = repository.getFeatureToggles(configBlank)

        assertNull(result)
    }

    @Test
    fun `getFeatureToggles when configBlank is null returns null`() {
        val result = repository.getFeatureToggles(null)

        assertNull(result)
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
            inappSettingsManager = mockk(relaxed = true),
            featureToggleManager = mockk(relaxed = true)
        )
    }
}
