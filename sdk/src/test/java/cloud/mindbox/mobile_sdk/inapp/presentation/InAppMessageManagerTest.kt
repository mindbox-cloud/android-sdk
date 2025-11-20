package cloud.mindbox.mobile_sdk.inapp.presentation

import android.util.Log
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.managers.UserVisitManager
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.MonitoringInteractor
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.sortByPriority
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
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

    /**
     * sets a thread to be used as main dispatcher for running on JVM
     * **/
    @Before
    fun onTestStart() {
        unmockkAll()
        Dispatchers.setMain(testDispatcher)
        mockkObject(MindboxPreferences)
        mockkObject(MindboxLoggerImpl)
        mockkStatic(Log::class)
        coEvery {
            inAppMessageInteractor.listenToTargetingEvents()
        } just runs
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
        inAppMessageManager = InAppMessageManagerImpl(
            inAppMessageViewDisplayer,
            inAppMessageInteractor,
            testDispatcher,
            monitoringRepository,
            sessionStorageManager,
            userVisitManager,
            inAppMessageDelayedManager
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
        inAppMessageManager = InAppMessageManagerImpl(
            inAppMessageViewDisplayer,
            inAppMessageInteractor,
            testDispatcher,
            monitoringRepository,
            sessionStorageManager,
            userVisitManager,
            inAppMessageDelayedManager
        )
        mockkObject(LoggingExceptionHandler)
        every { MindboxPreferences.inAppConfig } returns "test"
        coEvery {
            MindboxLoggerImpl.e(any(), any())
        } just runs
        val error = RuntimeException()
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
        val inAppToShowFlow = MutableSharedFlow<InApp>()
        val inApp = InAppStub.getInApp()
        every { inAppMessageViewDisplayer.isInAppActive() } returns false
        every { inAppMessageInteractor.areShowAndFrequencyLimitsAllowed(any()) } returns true
        every { inAppMessageDelayedManager.inAppToShowFlow } returns inAppToShowFlow
        every { inAppMessageDelayedManager.process(inApp) } coAnswers {
            this@runTest.launch {
                inAppToShowFlow.emit(inApp)
            }
        }

        inAppMessageManager = InAppMessageManagerImpl(
            inAppMessageViewDisplayer,
            inAppMessageInteractor,
            testDispatcher,
            monitoringRepository,
            sessionStorageManager,
            userVisitManager,
            inAppMessageDelayedManager
        )
        coEvery {
            inAppMessageInteractor.processEventAndConfig()
        }.answers {
            flow {
                emit(
                    inApp
                )
            }
        }

        inAppMessageManager.listenEventAndInApp()
        advanceUntilIdle()

        verify(exactly = 1) { inAppMessageDelayedManager.process(inApp) }
        verify(exactly = 1) { inAppMessageViewDisplayer.tryShowInAppMessage(inApp.form.variants.first(), any()) }
    }

    @Test
    fun `in app messages success message not shown when inApp already active`() = runTest {
        val inAppToShowFlow = MutableSharedFlow<InApp>()
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
            inAppMessageDelayedManager
        )
        coEvery {
            inAppMessageInteractor.listenToTargetingEvents()
        } just runs
        coEvery {
            inAppMessageInteractor.processEventAndConfig()
        }.answers {
            flow {
                emit(
                    inApp
                )
            }
        }
        every { inAppMessageDelayedManager.inAppToShowFlow } returns inAppToShowFlow
        every { inAppMessageDelayedManager.process(inApp) } answers {
            this@runTest.launch {
                inAppToShowFlow.emit(inApp)
            }
        }

        inAppMessageManager.listenEventAndInApp()
        advanceUntilIdle()
        verify(exactly = 1) { inAppMessageDelayedManager.process(inApp) }
        coVerify(exactly = 1) { inAppMessageInteractor.listenToTargetingEvents() }
        verify(exactly = 0) { inAppMessageViewDisplayer.tryShowInAppMessage(inApp.form.variants.first(), any()) }
    }

    @Test
    fun `in app messages success message not shown when inApp frequency or limits not allowed`() = runTest {
        val inAppToShowFlow = MutableSharedFlow<InApp>()
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
            inAppMessageDelayedManager
        )
        coEvery {
            inAppMessageInteractor.listenToTargetingEvents()
        } just runs
        coEvery {
            inAppMessageInteractor.processEventAndConfig()
        }.answers {
            flow {
                emit(
                    inApp
                )
            }
        }
        every { inAppMessageDelayedManager.inAppToShowFlow } returns inAppToShowFlow
        every { inAppMessageDelayedManager.process(inApp) } answers {
            this@runTest.launch {
                inAppToShowFlow.emit(inApp)
            }
        }

        inAppMessageManager.listenEventAndInApp()
        advanceUntilIdle()
        verify(exactly = 1) { inAppMessageDelayedManager.process(inApp) }
        coVerify(exactly = 1) { inAppMessageInteractor.listenToTargetingEvents() }
        verify(exactly = 0) { inAppMessageViewDisplayer.tryShowInAppMessage(inApp.form.variants.first(), any()) }
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
            inAppMessageDelayedManager
        )
        val exception = Exception()
        coEvery {
            inAppMessageInteractor.processEventAndConfig()
        } returns flow {
            throw exception
        }
        every {
            MindboxLoggerImpl.e(any(), any(), any())
        } just runs

        inAppMessageManager.listenEventAndInApp()
        advanceUntilIdle()

        verify(exactly = 1) {
            MindboxLoggerImpl.e(Mindbox, "Mindbox caught unhandled error", exception)
        }
    }

    private fun (() -> Any?).shouldNotThrow() = try {
        invoke()
    } catch (ex: Exception) {
        throw Error("expected not to throw!", ex)
    }

    @Test
    fun `in-app config throws network error non 404`() = runTest {
        inAppMessageManager = InAppMessageManagerImpl(
            inAppMessageViewDisplayer,
            inAppMessageInteractor,
            testDispatcher,
            monitoringRepository,
            sessionStorageManager,
            userVisitManager,
            inAppMessageDelayedManager
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
        inAppMessageManager = InAppMessageManagerImpl(
            inAppMessageViewDisplayer,
            inAppMessageInteractor,
            testDispatcher,
            monitoringRepository,
            sessionStorageManager,
            userVisitManager,
            inAppMessageDelayedManager
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
}
