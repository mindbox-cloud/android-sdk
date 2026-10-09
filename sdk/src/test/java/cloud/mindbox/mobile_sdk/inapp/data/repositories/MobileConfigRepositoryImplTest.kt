package cloud.mindbox.mobile_sdk.inapp.data.repositories

import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionState
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import cloud.mindbox.mobile_sdk.models.TimeSpan
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponseBlank
import cloud.mindbox.mobile_sdk.models.operation.response.SdkVersion
import io.mockk.*
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import cloud.mindbox.mobile_sdk.inapp.data.validators.TimeSpanPositiveValidator
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppConfig
import cloud.mindbox.mobile_sdk.models.InAppStub
import com.android.volley.NetworkResponse
import com.android.volley.VolleyError

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
    fun `getInApps keeps only the copy of a repeated id that this SDK version may show`() {
        val otherVersion = InAppStub.getInAppDtoBlank().copy(id = "story-2", sdkVersion = SdkVersion(minVersion = 999, maxVersion = null))
        val thisVersion = InAppStub.getInAppDtoBlank().copy(id = "story-2", sdkVersion = SdkVersion(minVersion = 1, maxVersion = null))
        val repository = createRepository(isVersionValid = { inAppDto -> inAppDto != otherVersion })

        repository.getInApps(InAppConfigResponseBlank(listOf(otherVersion, thisVersion), null, null, null))

        verify(exactly = 0) { inAppMapper.mapToInAppDto(otherVersion, any(), any(), any(), any(), any()) }
        verify(exactly = 1) { inAppMapper.mapToInAppDto(thisVersion, any(), any(), any(), any(), any()) }
    }

    @Test
    fun `hasConfig is false until a config has been provided`() {
        assertFalse(repository.hasConfig())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `findInAppInCurrentConfig is null until a config arrives, then answers from it with the first copy of a repeated id`() = withTestMindboxScope {
        val first = InAppStub.getInApp().copy(id = "story-2", tags = mapOf("copy" to "first"))
        val second = first.copy(tags = mapOf("copy" to "second"))
        val repository = createRepository()
        assertNull(repository.findInAppInCurrentConfig("story-2"))

        provideConfig(repository, InAppStub.getInApp().copy(id = "story-1"), first, second)

        assertEquals(first, repository.findInAppInCurrentConfig("story-2"))
        assertNull(repository.findInAppInCurrentConfig("story-3"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `findInAppInCurrentConfig is null again after resetCurrentConfig`() = withTestMindboxScope {
        val repository = createRepository()
        provideConfig(repository, InAppStub.getInApp().copy(id = "story-2"))
        assertNotNull(repository.findInAppInCurrentConfig("story-2"))

        repository.resetCurrentConfig()

        assertNull(repository.findInAppInCurrentConfig("story-2"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `findInAppInCurrentConfig answers from the config that replaced the previous one`() = withTestMindboxScope {
        val repository = createRepository()
        provideConfig(repository, InAppStub.getInApp().copy(id = "story-2", tags = mapOf("config" to "first")))

        every { inAppMapper.mapToInAppConfig(any()) } returns InAppConfig(
            inApps = listOf(InAppStub.getInApp().copy(id = "story-2", tags = mapOf("config" to "second"))),
            monitoring = emptyList(),
            operations = emptyMap(),
            abtests = emptyList(),
        )
        MindboxPreferences.inAppConfigFlow.emit("""{"next":true}""")

        assertEquals(mapOf("config" to "second"), repository.findInAppInCurrentConfig("story-2")?.tags)
    }

    private suspend fun provideConfig(repository: MobileConfigRepositoryImpl, vararg inApps: InApp) {
        every { inAppMapper.mapToInAppConfig(any()) } returns InAppConfig(
            inApps = inApps.toList(),
            monitoring = emptyList(),
            operations = emptyMap(),
            abtests = emptyList(),
        )
        repository.startListening()
        MindboxPreferences.inAppConfigFlow.emit("{}")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `startListening re-arms the config subscription killed with the sdk scope`() = withTestMindboxScope {
        val revived = createRepository()

        // The soft reinitialization: the scope dies with the subscription inside it.
        Mindbox.mindboxScope.cancel()
        setMindboxScope(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        revived.startListening()
        MindboxPreferences.inAppConfigFlow.emit("{}")

        assertTrue(revived.hasConfig())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `an emission that deserializes to nothing still concludes the wait with an empty config`() = withTestMindboxScope {
        // The fetch-failed fallback re-emits whatever is stored — an empty string when there is
        // no cache. That emission must answer the waiters with an empty config at once (the
        // block collapses fast, no wait_budget), never leave them hanging on configState.
        val repository = createRepository(deserializedBlank = null)
        repository.startListening()
        assertFalse(repository.hasConfig())

        MindboxPreferences.inAppConfigFlow.emit("")

        assertTrue(repository.hasConfig())
        assertTrue(repository.getInAppsSection().isEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun withTestMindboxScope(block: suspend TestScope.() -> Unit) = runTest {
        val originalScope = Mindbox.mindboxScope
        try {
            MindboxPreferences.inAppConfigFlow.resetReplayCache()
            setMindboxScope(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            block()
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `an empty emission after a failed fetch means the config is unavailable until a real one arrives`() = withTestMindboxScope {
        val repository = createRepository(
            deserializedBlank = null,
            sessionState = SessionState(configFetchingError = true),
        )
        repository.startListening()
        assertFalse(repository.hasConfig())

        MindboxPreferences.inAppConfigFlow.emit("")

        assertTrue(repository.hasConfig())
        assertNull(repository.getInAppsSectionIfAvailable())

        repository.resetCurrentConfig()

        assertFalse(repository.hasConfig())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `an empty emission without a fetch error is an empty config, not an unavailable one`() = withTestMindboxScope {
        val repository = createRepository(deserializedBlank = null, sessionState = SessionState(configFetchingError = false))
        repository.startListening()

        MindboxPreferences.inAppConfigFlow.emit("")

        assertTrue(repository.hasConfig())
        assertNotNull(repository.getInAppsSectionIfAvailable())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a real config after a failed fetch is available`() = withTestMindboxScope {
        val repository = createRepository(sessionState = SessionState(configFetchingError = true))
        repository.startListening()

        MindboxPreferences.inAppConfigFlow.emit("{}")

        assertTrue(repository.hasConfig())
        assertNotNull(repository.getInAppsSectionIfAvailable())
    }

    private class StoredConfig {
        var value: String = ""
        var writes: Long = 0L
    }

    private fun withStoredConfig(block: suspend TestScope.(StoredConfig) -> Unit) = withTestMindboxScope {
        val stored = StoredConfig()
        mockkObject(MindboxPreferences, DbManager)
        try {
            every { MindboxPreferences.inAppConfig } answers { stored.value }
            every { MindboxPreferences.inAppConfig = any() } answers {
                stored.value = firstArg()
                stored.writes++
            }
            every { MindboxPreferences.inAppConfigWrites } answers { stored.writes }
            every { MindboxPreferences.inAppConfigUpdatedTime = any() } just runs
            every { DbManager.listenConfigurations() } returns flowOf(mockk(relaxed = true))
            block(stored)
        } finally {
            unmockkObject(MindboxPreferences, DbManager)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a config stored before the session's config was reset is not published after the reset`() = withTestMindboxScope {
        val repository = createRepository()
        repository.startListening()

        repository.resetCurrentConfig()
        MindboxPreferences.inAppConfigFlow.emit("""{"stored":"before the reset"}""")

        assertFalse(repository.hasConfig())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `after a reset only the config the new session's download stored is published`() = withStoredConfig {
        val gateway = mockk<GatewayManager> { coEvery { fetchMobileConfig(any()) } returns "downloaded" }
        val repository = createRepository(gatewayManager = gateway)
        repository.startListening()
        repository.resetCurrentConfig()

        repository.fetchMobileConfig()
        MindboxPreferences.inAppConfigFlow.emit("stored before the reset")

        assertFalse(repository.hasConfig())

        MindboxPreferences.inAppConfigFlow.emit("downloaded")

        assertTrue(repository.hasConfig())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a download started before the reset stores nothing when it concludes after it`() = withStoredConfig { stored ->
        val download = CompletableDeferred<String>()
        val gateway = mockk<GatewayManager> { coEvery { fetchMobileConfig(any()) } coAnswers { download.await() } }
        val repository = createRepository(gatewayManager = gateway)
        val fetch = launch { repository.fetchMobileConfig() }
        runCurrent()

        repository.resetCurrentConfig()
        download.complete("downloaded in the ended session")
        fetch.join()

        assertEquals(0L, stored.writes)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `after a reset a failed download publishes the config its failure path stores again`() = withStoredConfig { stored ->
        stored.value = "cached"
        val gateway = mockk<GatewayManager> { coEvery { fetchMobileConfig(any()) } throws IllegalStateException("offline") }
        val repository = createRepository(gatewayManager = gateway)
        repository.startListening()
        repository.resetCurrentConfig()

        runCatching { repository.fetchMobileConfig() }
        MindboxPreferences.inAppConfigFlow.emit("stored even earlier")

        assertFalse(repository.hasConfig())

        MindboxPreferences.inAppConfigFlow.emit("cached")

        assertTrue(repository.hasConfig())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `in the window after a reset the page is answered at once from the config published before it, while blocks wait for the new one`() = withTestMindboxScope {
        val repository = createRepository()
        provideConfig(repository, InAppStub.getInApp().copy(id = "story-2"))
        repository.resetCurrentConfig()

        val forBlock = async { repository.getInAppsSectionIfAvailable() }
        runCurrent()

        assertEquals(listOf("story-2"), repository.getConfigForPageIfAvailable()?.inApps?.map { inApp -> inApp.id })
        assertFalse(forBlock.isCompleted)
        forBlock.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `before any config is published the page waits for the first one`() = withTestMindboxScope {
        val repository = createRepository()
        repository.startListening()

        val forPage = async { repository.getConfigForPageIfAvailable() }
        runCurrent()

        assertFalse(forPage.isCompleted)

        MindboxPreferences.inAppConfigFlow.emit("{}")
        runCurrent()

        assertNotNull(forPage.await())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `after a reset a download whose configuration cannot be read still concludes and publishes the stored config`() = withStoredConfig { stored ->
        stored.value = "cached"
        every { DbManager.listenConfigurations() } returns emptyFlow()
        val repository = createRepository()
        repository.startListening()
        repository.resetCurrentConfig()

        runCatching { repository.fetchMobileConfig() }
        MindboxPreferences.inAppConfigFlow.emit("cached")

        assertTrue(repository.hasConfig())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `after a reset the newest stored config stays published when an older write is emitted late`() = withStoredConfig {
        val gateway = mockk<GatewayManager> { coEvery { fetchMobileConfig(any()) } returns "first" }
        val repository = createRepository(gatewayManager = gateway)
        val publications = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.listenConfigUpdates().collect { publication -> publications.add(publication) }
        }
        repository.startListening()
        repository.resetCurrentConfig()
        repository.fetchMobileConfig()
        MindboxPreferences.inAppConfigFlow.emit("first")
        MindboxPreferences.inAppConfig = "second"
        MindboxPreferences.inAppConfigFlow.emit("second")

        MindboxPreferences.inAppConfigFlow.emit("first")

        assertEquals(2, publications.size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a download answered with 404 stores an empty config`() = withStoredConfig { stored ->
        stored.value = "cached"
        val gateway = mockk<GatewayManager> { coEvery { fetchMobileConfig(any()) } throws volleyError(statusCode = 404) }
        val repository = createRepository(gatewayManager = gateway)

        runCatching { repository.fetchMobileConfig() }

        assertEquals("", stored.value)
        assertEquals(1L, stored.writes)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a download failing on the network marks the fetch error and stores the cached config again`() = withStoredConfig { stored ->
        stored.value = "cached"
        val sessionState = SessionState()
        val gateway = mockk<GatewayManager> { coEvery { fetchMobileConfig(any()) } throws volleyError(statusCode = 503) }
        val repository = createRepository(gatewayManager = gateway, sessionState = sessionState)

        runCatching { repository.fetchMobileConfig() }

        assertTrue(sessionState.configFetchingError)
        assertEquals("cached", stored.value)
        assertEquals(1L, stored.writes)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a download of a session that has ended fails without storing anything or marking the new session's fetch error`() = withStoredConfig { stored ->
        val download = CompletableDeferred<String>()
        val sessionState = SessionState()
        val gateway = mockk<GatewayManager> { coEvery { fetchMobileConfig(any()) } coAnswers { download.await() } }
        val repository = createRepository(gatewayManager = gateway, sessionState = sessionState)
        val fetch = launch { runCatching { repository.fetchMobileConfig() } }
        runCurrent()

        repository.resetCurrentConfig()
        download.completeExceptionally(volleyError(statusCode = 503))
        fetch.join()

        assertEquals(0L, stored.writes)
        assertFalse(sessionState.configFetchingError)
    }

    private fun volleyError(statusCode: Int): VolleyError =
        VolleyError(NetworkResponse(statusCode, ByteArray(0), false, 0L, emptyList()))

    private fun createRepository(
        deserializedBlank: InAppConfigResponseBlank? = mockk(),
        sessionState: SessionState = SessionState(),
        isVersionValid: (InAppConfigResponseBlank.InAppDtoBlank) -> Boolean = { true },
        gatewayManager: GatewayManager = mockk(relaxed = true),
    ): MobileConfigRepositoryImpl {
        return MobileConfigRepositoryImpl(
            inAppMapper = inAppMapper,
            timeSpanPositiveValidator = TimeSpanPositiveValidator(),
            inAppConfigTtlValidator = mockk(relaxed = true) {
                every { isValid(any()) } returns true
            },
            inAppValidator = mockk(relaxed = true) {
                every { validateInAppVersion(any()) } answers { isVersionValid(firstArg()) }
                every { validateInApp(any()) } returns true
            },
            mobileConfigSerializationManager = mockk(relaxed = true) {
                every { deserializeToInAppTargetingDto(any(), any()) } returns mockk()
                every { deserializeToConfigDtoBlank(any()) } returns deserializedBlank
            },
            monitoringValidator = mockk(relaxed = true),
            abTestValidator = mockk(relaxed = true),
            operationNameValidator = mockk(relaxed = true),
            operationValidator = mockk(relaxed = true),
            gatewayManager = gatewayManager,
            defaultDataManager = mockk(relaxed = true) {
                every { fillFormData(any()) } returns mockk()
                every { fillFrequencyData(any()) } returns mockk()
            },
            ttlParametersValidator = mockk(relaxed = true),
            sessionStorageManager = mockk(relaxed = true) {
                every { state } returns sessionState
            },
            mobileConfigSettingsManager = mockk(relaxed = true),
            integerPositiveValidator = mockk(relaxed = true),
            inappSettingsManager = mockk(relaxed = true),
            featureToggleManager = mockk(relaxed = true),
            inAppWebViewPrewarmManager = mockk(relaxed = true)
        )
    }
}
