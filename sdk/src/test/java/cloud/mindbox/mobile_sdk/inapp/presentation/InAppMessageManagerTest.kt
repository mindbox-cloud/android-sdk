package cloud.mindbox.mobile_sdk.inapp.presentation

import android.util.Log
import cloud.mindbox.mobile_sdk.inapp.data.managers.SEND_INAPP_TAGS_FEATURE
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppActionCallbacks
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggleManager
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.managers.UserVisitManager
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.MonitoringInteractor
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.sortByPriority
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import cloud.mindbox.mobile_sdk.utils.SystemTimeProvider
import cloud.mindbox.mobile_sdk.utils.mockLogger
import cloud.mindbox.mobile_sdk.utils.mockPreferencesConfigSetter
import com.android.volley.NetworkResponse
import com.android.volley.VolleyError
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class InAppMessageManagerTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var inAppMessageInteractor: InAppInteractor

    @MockK
    private lateinit var inAppMessageViewDisplayer: InAppMessageViewDisplayer

    private lateinit var inAppMessageManager: InAppMessageManagerImpl

    @MockK
    private lateinit var monitoringRepository: MonitoringInteractor

    private val sessionStorageManager = mockk<SessionStorageManager>(relaxUnitFun = true)

    private val userVisitManager = mockk<UserVisitManager>()

    private val inAppMessageDelayedManager = mockk<InAppMessageDelayedManager>()

    private val testDispatcher = StandardTestDispatcher()

    private val timeProvider = mockk<SystemTimeProvider>()

    private val featureToggleManager = mockk<FeatureToggleManager>()

    /**
     * sets a thread to be used as main dispatcher for running on JVM
     * **/
    @Before
    fun onTestStart() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(MindboxPreferences)
        mockkStatic(Log::class)
        mockLogger()
        coEvery {
            inAppMessageInteractor.listenToTargetingEvents()
        } just runs
        every { featureToggleManager.isEnabled(any()) } returns true
        every {
            Log.isLoggable(any(), any())
        }.answers {
            true
        }
    }

    @After
    fun onTestFinish() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `in app config is being fetched`() = runTest {
        mockPreferencesConfigSetter()
        inAppMessageManager = InAppMessageManagerImpl(
            inAppMessageViewDisplayer,
            inAppMessageInteractor,
            testDispatcher,
            monitoringRepository,
            sessionStorageManager,
            userVisitManager,
            inAppMessageDelayedManager,
            timeProvider,
            featureToggleManager
        )
        coEvery {
            inAppMessageInteractor.fetchMobileConfig()
        } just runs
        inAppMessageManager.requestConfig()
        advanceUntilIdle();
        {
            coVerify(exactly = 1) { inAppMessageInteractor.fetchMobileConfig() }
        }.shouldNotThrow()
    }

    @Test
    fun `in-app config throws non network error`() = runTest {
        mockPreferencesConfigSetter()
        inAppMessageManager = InAppMessageManagerImpl(
            inAppMessageViewDisplayer,
            inAppMessageInteractor,
            testDispatcher,
            monitoringRepository,
            sessionStorageManager,
            userVisitManager,
            inAppMessageDelayedManager,
            timeProvider,
            featureToggleManager
        )
        mockkObject(LoggingExceptionHandler)
        every { MindboxPreferences.inAppConfig } returns "test"
        val error = Error()
        coEvery {
            inAppMessageInteractor.fetchMobileConfig()
        }.throws(error)
        inAppMessageManager.requestConfig()
        advanceUntilIdle()
        verify(exactly = 1) {
            MindboxLoggerImpl.e(InAppMessageManagerImpl::class, "Failed to get config", error)
        }
        verify(exactly = 1) {
            MindboxPreferences setProperty MindboxPreferences::inAppConfig.name value "test"
        }
    }

    @Test
    fun `in app messages success message shown`() = runTest {
        val inAppToShowFlow = MutableSharedFlow<Pair<InApp, Milliseconds>>()
        val inApp = InAppStub.getInApp()
        every { inAppMessageViewDisplayer.isInAppActive() } returns false
        every { inAppMessageInteractor.areShowAndFrequencyLimitsAllowed(any()) } returns true
        every { inAppMessageDelayedManager.inAppToShowFlow } returns inAppToShowFlow
        every { inAppMessageDelayedManager.process(inApp, any()) } coAnswers {
            this@runTest.launch {
                inAppToShowFlow.emit(inApp to Milliseconds(0L))
            }
        }

        inAppMessageManager = InAppMessageManagerImpl(
            inAppMessageViewDisplayer,
            inAppMessageInteractor,
            testDispatcher,
            monitoringRepository,
            sessionStorageManager,
            userVisitManager,
            inAppMessageDelayedManager,
            timeProvider,
            featureToggleManager
        )
        coEvery {
            inAppMessageInteractor.processEventAndConfig()
        }.answers {
            flow {
                emit(inApp to Milliseconds(0L))
            }
        }

        inAppMessageManager.listenEventAndInApp()
        advanceUntilIdle()

        verify(exactly = 1) { inAppMessageDelayedManager.process(inApp, any()) }
        verify(exactly = 1) { inAppMessageViewDisplayer.tryShowInAppMessage(inApp.form.variants.first(), any(), any(), any()) }
    }

    @Test
    fun `in app messages success message not shown when inApp already active`() = runTest {
        val inAppToShowFlow = MutableSharedFlow<Pair<InApp, Milliseconds>>()
        val inApp = InAppStub.getInApp()
        every { inAppMessageInteractor.areShowAndFrequencyLimitsAllowed(any()) } returns true
        every { inAppMessageViewDisplayer.isInAppActive() } returns true
        inAppMessageManager = InAppMessageManagerImpl(
            inAppMessageViewDisplayer,
            inAppMessageInteractor,
            testDispatcher,
            monitoringRepository,
            sessionStorageManager,
            userVisitManager,
            inAppMessageDelayedManager,
            timeProvider,
            featureToggleManager
        )
        coEvery {
            inAppMessageInteractor.listenToTargetingEvents()
        } just runs
        coEvery {
            inAppMessageInteractor.processEventAndConfig()
        }.answers {
            flow {
                emit(inApp to Milliseconds(0L))
            }
        }
        every { inAppMessageDelayedManager.inAppToShowFlow } returns inAppToShowFlow
        every { inAppMessageDelayedManager.process(inApp, any()) } answers {
            this@runTest.launch {
                inAppToShowFlow.emit(inApp to Milliseconds(0L))
            }
        }

        inAppMessageManager.listenEventAndInApp()
        advanceUntilIdle()
        verify(exactly = 1) { inAppMessageDelayedManager.process(inApp, any()) }
        coVerify(exactly = 1) { inAppMessageInteractor.listenToTargetingEvents() }
        verify(exactly = 0) { inAppMessageViewDisplayer.tryShowInAppMessage(inApp.form.variants.first(), any(), any(), any()) }
    }

    @Test
    fun `in app messages success message not shown when inApp frequency or limits not allowed`() = runTest {
        val inAppToShowFlow = MutableSharedFlow<Pair<InApp, Milliseconds>>()
        val inApp = InAppStub.getInApp()
        every { inAppMessageInteractor.areShowAndFrequencyLimitsAllowed(any()) } returns false
        every { inAppMessageViewDisplayer.isInAppActive() } returns false
        inAppMessageManager = InAppMessageManagerImpl(
            inAppMessageViewDisplayer,
            inAppMessageInteractor,
            testDispatcher,
            monitoringRepository,
            sessionStorageManager,
            userVisitManager,
            inAppMessageDelayedManager,
            timeProvider,
            featureToggleManager
        )
        coEvery {
            inAppMessageInteractor.listenToTargetingEvents()
        } just runs
        coEvery {
            inAppMessageInteractor.processEventAndConfig()
        }.answers {
            flow {
                emit(inApp to Milliseconds(0L))
            }
        }
        every { inAppMessageDelayedManager.inAppToShowFlow } returns inAppToShowFlow
        every { inAppMessageDelayedManager.process(inApp, any()) } answers {
            this@runTest.launch {
                inAppToShowFlow.emit(inApp to Milliseconds(0L))
            }
        }

        inAppMessageManager.listenEventAndInApp()
        advanceUntilIdle()
        verify(exactly = 1) { inAppMessageDelayedManager.process(inApp, any()) }
        coVerify(exactly = 1) { inAppMessageInteractor.listenToTargetingEvents() }
        verify(exactly = 0) { inAppMessageViewDisplayer.tryShowInAppMessage(inApp.form.variants.first(), any(), any(), any()) }
    }

    @Test
    fun `in app messages error message`() = runTest {
        inAppMessageManager = InAppMessageManagerImpl(
            inAppMessageViewDisplayer,
            inAppMessageInteractor,
            testDispatcher,
            monitoringRepository,
            sessionStorageManager,
            userVisitManager,
            inAppMessageDelayedManager,
            timeProvider,
            featureToggleManager
        )
        coEvery {
            inAppMessageInteractor.processEventAndConfig()
        } returns flow {
            throw Error("test error")
        }
        try {
            inAppMessageManager.listenEventAndInApp()
            advanceUntilIdle()
        } catch (e: Error) {
            e.printStackTrace()
        }
        coVerify(exactly = 1) {
            inAppMessageInteractor.listenToTargetingEvents()
        }
    }

    private fun (() -> Any?).shouldNotThrow() = try {
        invoke()
    } catch (ex: Exception) {
        throw Error("expected not to throw!", ex)
    }

    @Test
    fun `in-app config throws network error non 404`() = runTest {
        mockPreferencesConfigSetter()
        inAppMessageManager = InAppMessageManagerImpl(
            inAppMessageViewDisplayer,
            inAppMessageInteractor,
            testDispatcher,
            monitoringRepository,
            sessionStorageManager,
            userVisitManager,
            inAppMessageDelayedManager,
            timeProvider,
            featureToggleManager
        )
        mockkConstructor(NetworkResponse::class)
        val networkResponse = mockk<NetworkResponse>()
        NetworkResponse::class.java.getDeclaredField("statusCode").apply {
            isAccessible = true
            setInt(networkResponse, 403)
        }
        every {
            MindboxPreferences getProperty MindboxPreferences::inAppConfig.name
        }.answers {
            "test"
        }
        coEvery {
            inAppMessageInteractor.fetchMobileConfig()
        }.throws(VolleyError(networkResponse))
        inAppMessageManager.requestConfig()
        advanceUntilIdle()
        verify(exactly = 1) { sessionStorageManager.configFetchingError = true }
        verify(exactly = 1) {
            MindboxPreferences setProperty MindboxPreferences::inAppConfig.name value "test"
        }
    }

    @Test
    fun `in app config throws network error 404`() = runTest {
        mockPreferencesConfigSetter()
        inAppMessageManager = InAppMessageManagerImpl(
            inAppMessageViewDisplayer,
            inAppMessageInteractor,
            testDispatcher,
            monitoringRepository,
            sessionStorageManager,
            userVisitManager,
            inAppMessageDelayedManager,
            timeProvider,
            featureToggleManager
        )
        mockkConstructor(NetworkResponse::class)
        val networkResponse = mockk<NetworkResponse>()
        NetworkResponse::class.java.getDeclaredField("statusCode").apply {
            isAccessible = true
            setInt(networkResponse, 404)
        }
        coEvery {
            inAppMessageInteractor.fetchMobileConfig()
        }.throws(VolleyError(networkResponse))
        inAppMessageManager.requestConfig()
        advanceUntilIdle()
        verify(exactly = 0) { sessionStorageManager.configFetchingError = true }
        verify(exactly = 1) {
            MindboxPreferences setProperty MindboxPreferences::inAppConfig.name value ""
        }
    }

    @Test
    fun `sorting by priority save order of inapps from config`() {
        val inApp1 = InAppStub.getInApp().copy(id = "inApp1_priority_false", isPriority = false)
        val inApp2 = InAppStub.getInApp().copy(id = "inApp2_priority_true", isPriority = true)
        val inApp3 = InAppStub.getInApp().copy(id = "inApp3_priority_false", isPriority = false)
        val inApp4 = InAppStub.getInApp().copy(id = "inApp4_priority_true", isPriority = true)
        val inApp5 = InAppStub.getInApp().copy(id = "inApp5_priority_false", isPriority = false)
        val inApp6 = InAppStub.getInApp().copy(id = "inApp6_priority_true", isPriority = true)
        val inappsFromConfig = listOf(inApp1, inApp2, inApp3, inApp4, inApp5, inApp6)
        val expectedInappList = listOf(inApp2, inApp4, inApp6, inApp1, inApp3, inApp5)

        val resultInappList = inappsFromConfig.sortByPriority()

        assertEquals(expectedInappList, resultInappList)
    }

    @Test
    fun `tags feature on - tags passed to show and click`() = runTest {
        val tags = mapOf("templateType" to "Popup")
        val inAppToShowFlow = MutableSharedFlow<Pair<InApp, Milliseconds>>()
        val inApp = InAppStub.getInApp().copy(tags = tags)
        val inAppMessage = inApp.form.variants.first()
        var capturedCallbacks: InAppActionCallbacks? = null
        var capturedTags: Map<String, String>? = null
        every { featureToggleManager.isEnabled(SEND_INAPP_TAGS_FEATURE) } returns true
        every { inAppMessageViewDisplayer.isInAppActive() } returns false
        every { inAppMessageInteractor.areShowAndFrequencyLimitsAllowed(any()) } returns true
        every { inAppMessageInteractor.sendInAppClicked(any(), any()) } just runs
        every { inAppMessageDelayedManager.inAppToShowFlow } returns inAppToShowFlow
        every { inAppMessageDelayedManager.process(inApp, any()) } coAnswers {
            this@runTest.launch { inAppToShowFlow.emit(inApp to Milliseconds(0L)) }
        }
        every { inAppMessageViewDisplayer.tryShowInAppMessage(any(), any(), any(), any()) } answers {
            capturedCallbacks = arg(1)
            capturedTags = arg(3)
        }
        inAppMessageManager = InAppMessageManagerImpl(
            inAppMessageViewDisplayer,
            inAppMessageInteractor,
            testDispatcher,
            monitoringRepository,
            sessionStorageManager,
            userVisitManager,
            inAppMessageDelayedManager,
            timeProvider,
            featureToggleManager
        )
        coEvery { inAppMessageInteractor.processEventAndConfig() } answers {
            flow { emit(inApp to Milliseconds(0L)) }
        }

        inAppMessageManager.listenEventAndInApp()
        advanceUntilIdle()

        assertEquals(tags, capturedTags)
        capturedCallbacks!!.onInAppClick.onClick()
        verify(exactly = 1) { inAppMessageInteractor.sendInAppClicked(inAppMessage.inAppId, tags) }
    }

    @Test
    fun `tags feature off - tags are null for show and click`() = runTest {
        val inAppToShowFlow = MutableSharedFlow<Pair<InApp, Milliseconds>>()
        val inApp = InAppStub.getInApp().copy(tags = mapOf("templateType" to "Popup"))
        val inAppMessage = inApp.form.variants.first()
        var capturedCallbacks: InAppActionCallbacks? = null
        var capturedTags: Map<String, String>? = mapOf("sentinel" to "value")
        every { featureToggleManager.isEnabled(SEND_INAPP_TAGS_FEATURE) } returns false
        every { inAppMessageViewDisplayer.isInAppActive() } returns false
        every { inAppMessageInteractor.areShowAndFrequencyLimitsAllowed(any()) } returns true
        every { inAppMessageInteractor.sendInAppClicked(any(), any()) } just runs
        every { inAppMessageDelayedManager.inAppToShowFlow } returns inAppToShowFlow
        every { inAppMessageDelayedManager.process(inApp, any()) } coAnswers {
            this@runTest.launch { inAppToShowFlow.emit(inApp to Milliseconds(0L)) }
        }
        every { inAppMessageViewDisplayer.tryShowInAppMessage(any(), any(), any(), any()) } answers {
            capturedCallbacks = arg(1)
            capturedTags = arg(3)
        }
        inAppMessageManager = InAppMessageManagerImpl(
            inAppMessageViewDisplayer,
            inAppMessageInteractor,
            testDispatcher,
            monitoringRepository,
            sessionStorageManager,
            userVisitManager,
            inAppMessageDelayedManager,
            timeProvider,
            featureToggleManager
        )
        coEvery { inAppMessageInteractor.processEventAndConfig() } answers {
            flow { emit(inApp to Milliseconds(0L)) }
        }

        inAppMessageManager.listenEventAndInApp()
        advanceUntilIdle()

        assertEquals(null, capturedTags)
        capturedCallbacks!!.onInAppClick.onClick()
        verify(exactly = 1) { inAppMessageInteractor.sendInAppClicked(inAppMessage.inAppId, null) }
    }
}
