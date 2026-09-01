package cloud.mindbox.mobile_sdk.inapp.data.repositories

import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
        verify(exactly = 1) { inAppMapper.mapToInAppDto(any(), capture(slot), any(), any(), any(), any()) }
        assertEquals("00:30:00", slot.captured.value)
    }

    @Test
    fun `getInApps when delayTime is negative string then passes null to mapper`() {
        val testDto = InAppStub.getInAppDtoBlank().copy(delayTime = "-00:30:00")
        val configBlank = InAppConfigResponseBlank(listOf(testDto), null, null, null)

        repository.getInApps(configBlank)

        verify(exactly = 1) { inAppMapper.mapToInAppDto(any(), null, any(), any(), any(), any()) }
    }

    @Test
    fun `getInApps when delayTime is zero string then passes null to mapper`() {
        val testDto = InAppStub.getInAppDtoBlank().copy(delayTime = "00:00:00")
        val configBlank = InAppConfigResponseBlank(listOf(testDto), null, null, null)

        repository.getInApps(configBlank)

        verify(exactly = 1) { inAppMapper.mapToInAppDto(any(), null, any(), any(), any(), any()) }
    }

    @Test
    fun `getInApps when delayTime is null then passes null to mapper`() {
        val testDto = InAppStub.getInAppDtoBlank().copy(delayTime = null)
        val configBlank = InAppConfigResponseBlank(listOf(testDto), null, null, null)

        repository.getInApps(configBlank)

        verify(exactly = 1) { inAppMapper.mapToInAppDto(any(), null, any(), any(), any(), any()) }
    }

    @Test
    fun `hasConfig is false until a config has been provided`() {
        assertFalse(repository.hasConfig())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `startListening re-arms the config subscription killed with the sdk scope`() = runTest {
        val originalScope = Mindbox.mindboxScope
        try {
            MindboxPreferences.inAppConfigFlow.resetReplayCache()
            setMindboxScope(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            val revived = createRepository()

            // The soft reinitialization: the scope dies with the subscription inside it.
            Mindbox.mindboxScope.cancel()
            setMindboxScope(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

            revived.startListening()
            MindboxPreferences.inAppConfigFlow.emit("{}")

            assertTrue(revived.hasConfig())
        } finally {
            setMindboxScope(originalScope)
            MindboxPreferences.inAppConfigFlow.resetReplayCache()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `an emission that deserializes to nothing still concludes the wait with an empty config`() = runTest {
        // The fetch-failed fallback re-emits whatever is stored — an empty string when there is
        // no cache. That emission must answer the waiters with an empty config at once (the
        // block collapses fast, no wait_budget), never leave them hanging on configState.
        val originalScope = Mindbox.mindboxScope
        try {
            MindboxPreferences.inAppConfigFlow.resetReplayCache()
            setMindboxScope(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            val repository = createRepository(deserializedBlank = null)
            repository.startListening()
            assertFalse(repository.hasConfig())

            MindboxPreferences.inAppConfigFlow.emit("")

            assertTrue(repository.hasConfig())
            assertTrue(repository.getInAppsSection().isEmpty())
        } finally {
            setMindboxScope(originalScope)
            MindboxPreferences.inAppConfigFlow.resetReplayCache()
        }
    }

    private fun setMindboxScope(scope: CoroutineScope) {
        Mindbox::class.java.getDeclaredField("mindboxScope")
            .apply { isAccessible = true }
            .set(Mindbox, scope)
    }

    private fun createRepository(
        deserializedBlank: InAppConfigResponseBlank? = mockk(),
    ): MobileConfigRepositoryImpl {
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
                every { deserializeToConfigDtoBlank(any()) } returns deserializedBlank
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
            featureToggleManager = mockk(relaxed = true),
            inAppWebViewPrewarmManager = mockk(relaxed = true)
        )
    }
}
