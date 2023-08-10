package cloud.mindbox.mobile_sdk.inapp.presentation

import android.util.Log
import app.cash.turbine.test
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.MonitoringInteractor
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.android.volley.NetworkResponse
import com.android.volley.VolleyError
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.After
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

    @OptIn(DelicateCoroutinesApi::class)
    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    /**
     * sets a thread to be used as main dispatcher for running on JVM
     * **/
    @Before
    fun onTestStart() {
        Dispatchers.setMain(mainThreadSurrogate)
        mockkObject(MindboxPreferences)
        mockkObject(MindboxLoggerImpl)
        mockkStatic(Log::class)
        every {
            Log.isLoggable(any(), any())
        }.answers {
            true
        }
    }

    @After
    fun onTestFinish() {
        Dispatchers.resetMain()
        mainThreadSurrogate.close()
    }

    @Test
    fun `in app config is being fetched`() = runTest {
        inAppMessageManager = InAppMessageManagerImpl(
            inAppMessageViewDisplayer,
            inAppMessageInteractor,
            StandardTestDispatcher(testScheduler), monitoringRepository
        )
        coEvery {
            inAppMessageInteractor.fetchMobileConfig()

        } just runs
        inAppMessageManager.requestConfig()
        advanceUntilIdle();
        {
            coVerify(exactly = 1)
            {
                inAppMessageInteractor.fetchMobileConfig()
            }
        }.shouldNotThrow()
    }

    @Test
    fun `in-app config throws non network error`() = runTest {
        inAppMessageManager = InAppMessageManagerImpl(
            inAppMessageViewDisplayer,
            inAppMessageInteractor,
            StandardTestDispatcher(testScheduler), monitoringRepository
        )
        mockkObject(LoggingExceptionHandler)
        coEvery {
            MindboxLoggerImpl.e(any(), any())
        } just runs
        val error = Error()
        coEvery {
            inAppMessageInteractor.fetchMobileConfig()
        }.throws(error)
        inAppMessageManager.requestConfig()
        advanceUntilIdle()
        verify(exactly = 1) {
            MindboxLoggerImpl.e(InAppMessageManagerImpl::class, "Failed to get config", error)
        }
    }

    @Test
    fun `in app messages success message not shown`() = runTest {
        every { inAppMessageInteractor.isInAppShown() } returns false
        every { inAppMessageViewDisplayer.isInAppActive() } returns false
        inAppMessageManager = InAppMessageManagerImpl(
            inAppMessageViewDisplayer,
            inAppMessageInteractor,
            StandardTestDispatcher(testScheduler),
            monitoringRepository
        )
        every {
            runBlocking {
                inAppMessageInteractor.processEventAndConfig()
            }
        }.answers {
            flow {
                emit(
                    InAppStub.getModalWindow()
                )
            }
        }
        inAppMessageManager.listenEventAndInApp()
        advanceUntilIdle()
        inAppMessageInteractor.processEventAndConfig().test {
            awaitItem()
            awaitComplete()
        }
        every {
            inAppMessageViewDisplayer.tryShowInAppMessage(any(), any(), any())
        } just runs
        verify(exactly = 1) {
            inAppMessageViewDisplayer.tryShowInAppMessage(any(), any(), any())
        }
    }

    @Test
    fun `in app messages success message shown`() = runTest {
        every { inAppMessageInteractor.isInAppShown() } returns true
        inAppMessageManager = InAppMessageManagerImpl(
            inAppMessageViewDisplayer,
            inAppMessageInteractor,
            StandardTestDispatcher(testScheduler),
            monitoringRepository
        )
        every {
            runBlocking {
                inAppMessageInteractor.processEventAndConfig()
            }
        }.answers {
            flow {
                emit(
                    InAppStub.getModalWindow()
                )
            }
        }
        inAppMessageManager.listenEventAndInApp()
        advanceUntilIdle()
        inAppMessageInteractor.processEventAndConfig().test {
            awaitItem()
            awaitComplete()
        }
        every {
            inAppMessageViewDisplayer.tryShowInAppMessage(any(), any(), any())
        } just runs
        verify(exactly = 0) {
            inAppMessageViewDisplayer.tryShowInAppMessage(any(), any(), any())
        }
    }

    @Test
    fun `in app messages error message`() = runTest {
        inAppMessageManager = InAppMessageManagerImpl(
            inAppMessageViewDisplayer,
            inAppMessageInteractor,
            StandardTestDispatcher(testScheduler), monitoringRepository
        )
        every {
            runBlocking {
                inAppMessageInteractor.processEventAndConfig()
            }
        }.answers {
            flow {
                error("test error")
            }
        }
        every {
            MindboxLoggerImpl.e(any(), any(), any())
        } just runs
        inAppMessageManager.listenEventAndInApp()
        advanceUntilIdle()
        inAppMessageInteractor.processEventAndConfig().test {
            awaitError()
            verify(exactly = 1) {
                MindboxLoggerImpl.e(Mindbox, "Mindbox caught unhandled error", any())
            }
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
            StandardTestDispatcher(testScheduler), monitoringRepository
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
        verify(exactly = 1) {
            MindboxPreferences setProperty MindboxPreferences::inAppConfig.name value "test"
        }
    }

    @Test
    fun `in app config throws network error 404`() = runTest {
        inAppMessageManager = InAppMessageManagerImpl(
            inAppMessageViewDisplayer,
            inAppMessageInteractor,
            StandardTestDispatcher(testScheduler), monitoringRepository
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
        verify(exactly = 1) {
            MindboxPreferences setProperty MindboxPreferences::inAppConfig.name value ""
        }
    }


}

