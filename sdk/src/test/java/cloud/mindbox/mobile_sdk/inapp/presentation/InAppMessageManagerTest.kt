package cloud.mindbox.mobile_sdk.inapp.presentation

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.ShowReservationOutcome
import android.util.Log
import cloud.mindbox.mobile_sdk.inapp.data.managers.SEND_INAPP_TAGS_FEATURE
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppActionCallbacks
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppToShow
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggleManager
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.managers.UserVisitManager
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.Timestamp
import com.google.gson.JsonPrimitive
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
import org.junit.Assert.assertTrue
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

    private val timeProvider = mockk<SystemTimeProvider> {
        every { monotonicMillis() } returns Milliseconds(0L)
        every { monotonicElapsedSince(any()) } returns Milliseconds(0L)
    }

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
        every { inAppMessageInteractor.reserveOverlayShow(any()) } returns ShowReservationOutcome.GRANTED
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
        every { inAppMessageInteractor.reserveOverlayShow(any()) } returns ShowReservationOutcome.GRANTED
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
        every { inAppMessageInteractor.reserveOverlayShow(any()) } returns ShowReservationOutcome.REFUSED
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
        every { inAppMessageInteractor.reserveOverlayShow(any()) } returns ShowReservationOutcome.GRANTED
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
        every { inAppMessageInteractor.reserveOverlayShow(any()) } returns ShowReservationOutcome.GRANTED
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

    private fun TestScope.createManager(): InAppMessageManagerImpl = InAppMessageManagerImpl(
        inAppMessageViewDisplayer,
        inAppMessageInteractor,
        StandardTestDispatcher(testScheduler),
        monitoringRepository,
        sessionStorageManager,
        userVisitManager,
        inAppMessageDelayedManager,
        timeProvider,
        featureToggleManager
    )

    @Test
    fun `showInAppById presents the variant now with its tags and the caller params`() = runTest {
        val inApp = InAppStub.getInApp().copy(id = "tap-id", tags = mapOf("key" to "value"))
        val variant = inApp.form.variants.first()
        val extraParams = mapOf("title" to JsonPrimitive("Сториз 1"))
        coEvery { inAppMessageInteractor.getInAppToShowById("tap-id") } returns InAppToShow(inApp, variant)
        every {
            inAppMessageViewDisplayer.showInAppMessageNow(any(), any(), any(), any(), any())
        } just runs

        createManager().showInAppById("tap-id", extraParams) {}
        advanceUntilIdle()

        verify(exactly = 1) {
            inAppMessageViewDisplayer.showInAppMessageNow(
                variant,
                any(),
                any(),
                mapOf("key" to "value"),
                extraParams
            )
        }
    }

    @Test
    fun `showInAppById wires the ordinary show and dismiss accounting`() = runTest {
        val inApp = InAppStub.getInApp().copy(id = "tap-id")
        val variant = inApp.form.variants.first()
        coEvery { inAppMessageInteractor.getInAppToShowById("tap-id") } returns InAppToShow(inApp, variant)
        every { timeProvider.currentTimestamp() } returns Timestamp(100L)
        every { inAppMessageInteractor.saveShownInApp(any(), any(), any(), any()) } just runs
        every { inAppMessageInteractor.saveInAppDismissTime(any()) } just runs
        every { inAppMessageInteractor.sendInAppClicked(any(), any()) } just runs
        val callbacks = slot<InAppActionCallbacks>()
        every {
            inAppMessageViewDisplayer.showInAppMessageNow(any(), capture(callbacks), any(), any(), any())
        } just runs

        createManager().showInAppById("tap-id", emptyMap()) {}
        advanceUntilIdle()

        callbacks.captured.onInAppShown.onShown()
        verify(exactly = 1) { inAppMessageInteractor.saveShownInApp(variant.inAppId, 100L, any(), null) }
        callbacks.captured.onInAppDismiss.onDismiss()
        verify(exactly = 1) { inAppMessageInteractor.saveInAppDismissTime(inApp) }
        callbacks.captured.onInAppClick.onClick()
        verify(exactly = 1) { inAppMessageInteractor.sendInAppClicked(variant.inAppId, null) }
    }

    @Test
    fun `showInAppById for an id nothing resolves shows nothing and answers unknown_inapp`() = runTest {
        coEvery { inAppMessageInteractor.getInAppToShowById("missing") } returns null
        val outcomes = mutableListOf<ShowInAppOutcome>()

        createManager().showInAppById("missing", emptyMap()) { outcomes.add(it) }
        advanceUntilIdle()

        verify(exactly = 0) {
            inAppMessageViewDisplayer.showInAppMessageNow(any(), any(), any(), any(), any())
        }
        assertEquals(listOf(ShowInAppOutcome.NotShown(ShowInAppFailure.UNKNOWN_INAPP)), outcomes)
    }

    @Test
    fun `showInAppById answers shown once the window is on screen, and only then`() = runTest {
        val inApp = InAppStub.getInApp().copy(id = "tap-id")
        val variant = inApp.form.variants.first()
        coEvery { inAppMessageInteractor.getInAppToShowById("tap-id") } returns InAppToShow(inApp, variant)
        every { timeProvider.currentTimestamp() } returns Timestamp(100L)
        every { inAppMessageInteractor.saveShownInApp(any(), any(), any(), any()) } just runs
        every { inAppMessageInteractor.saveInAppDismissTime(any()) } just runs
        val callbacks = slot<InAppActionCallbacks>()
        every {
            inAppMessageViewDisplayer.showInAppMessageNow(any(), capture(callbacks), any(), any(), any())
        } just runs
        val outcomes = mutableListOf<ShowInAppOutcome>()

        createManager().showInAppById("tap-id", emptyMap()) { outcomes.add(it) }
        advanceUntilIdle()
        assertTrue(outcomes.isEmpty())

        callbacks.captured.onInAppShown.onShown()
        assertEquals(listOf(ShowInAppOutcome.Shown), outcomes)

        // A dismiss after the show is an ordinary dismiss, not a second answer.
        callbacks.captured.onInAppDismiss.onDismiss()
        assertEquals(listOf(ShowInAppOutcome.Shown), outcomes)
    }

    @Test
    fun `showInAppById answers show_failed once when the show never happens`() = runTest {
        val inApp = InAppStub.getInApp().copy(id = "tap-id")
        val variant = inApp.form.variants.first()
        coEvery { inAppMessageInteractor.getInAppToShowById("tap-id") } returns InAppToShow(inApp, variant)
        every { timeProvider.currentTimestamp() } returns Timestamp(100L)
        every { inAppMessageInteractor.saveShownInApp(any(), any(), any(), any()) } just runs
        val callbacks = slot<InAppActionCallbacks>()
        every {
            inAppMessageViewDisplayer.showInAppMessageNow(any(), capture(callbacks), any(), any(), any())
        } just runs
        val outcomes = mutableListOf<ShowInAppOutcome>()

        createManager().showInAppById("tap-id", emptyMap()) { outcomes.add(it) }
        advanceUntilIdle()

        callbacks.captured.onInAppNotShown.onNotShown()
        callbacks.captured.onInAppNotShown.onNotShown()
        callbacks.captured.onInAppShown.onShown()

        assertEquals(listOf(ShowInAppOutcome.NotShown(ShowInAppFailure.SHOW_FAILED)), outcomes)
    }

    // ---- the overlay's hold in the show budgets: taken before the show, given back if it never shows ----

    private fun TestScope.managerWithCapturedCallbacks(
        inApp: InApp,
        hold: ShowReservationOutcome = ShowReservationOutcome.GRANTED,
    ): () -> InAppActionCallbacks {
        val inAppToShowFlow = MutableSharedFlow<Pair<InApp, Milliseconds>>()
        var captured: InAppActionCallbacks? = null
        every { featureToggleManager.isEnabled(SEND_INAPP_TAGS_FEATURE) } returns false
        every { inAppMessageViewDisplayer.isInAppActive() } returns false
        every { inAppMessageInteractor.reserveOverlayShow(any()) } returns hold
        every { inAppMessageInteractor.releaseOverlayShow(any()) } just runs
        every { inAppMessageInteractor.saveInAppDismissTime(any()) } just runs
        every { inAppMessageInteractor.saveShownInApp(any(), any(), any(), any()) } just runs
        every { timeProvider.currentTimestamp() } returns Timestamp(1_000L)
        every { timeProvider.currentTimeMillis() } returns 1_000L
        every { inAppMessageDelayedManager.inAppToShowFlow } returns inAppToShowFlow
        every { inAppMessageDelayedManager.process(inApp, any()) } coAnswers {
            this@managerWithCapturedCallbacks.launch { inAppToShowFlow.emit(inApp to Milliseconds(0L)) }
        }
        every { inAppMessageViewDisplayer.tryShowInAppMessage(any(), any(), any(), any()) } answers {
            captured = arg(1)
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
        coEvery { inAppMessageInteractor.processEventAndConfig() } answers { flow { emit(inApp to Milliseconds(0L)) } }
        inAppMessageManager.listenEventAndInApp()
        advanceUntilIdle()
        return { captured!! }
    }

    @Test
    fun `a candidate refused by the budgets is not handed to the displayer`() = runTest {
        val inAppToShowFlow = MutableSharedFlow<Pair<InApp, Milliseconds>>()
        val inApp = InAppStub.getInApp()
        every { inAppMessageViewDisplayer.isInAppActive() } returns false
        every { inAppMessageInteractor.reserveOverlayShow(any()) } returns ShowReservationOutcome.REFUSED
        every { inAppMessageDelayedManager.inAppToShowFlow } returns inAppToShowFlow
        every { inAppMessageDelayedManager.process(inApp, any()) } coAnswers {
            this@runTest.launch { inAppToShowFlow.emit(inApp to Milliseconds(0L)) }
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
        coEvery { inAppMessageInteractor.processEventAndConfig() } answers { flow { emit(inApp to Milliseconds(0L)) } }

        inAppMessageManager.listenEventAndInApp()
        advanceUntilIdle()

        verify(exactly = 1) { inAppMessageInteractor.reserveOverlayShow(inApp) }
        verify(exactly = 0) { inAppMessageViewDisplayer.tryShowInAppMessage(any(), any(), any(), any()) }
    }

    @Test
    fun `a candidate the displayer will never present gives its hold back`() = runTest {
        val inApp = InAppStub.getInApp()
        val callbacks = managerWithCapturedCallbacks(inApp)

        callbacks().onInAppNotShown.onNotShown()

        verify(exactly = 1) { inAppMessageInteractor.releaseOverlayShow(inApp.id) }
    }

    @Test
    fun `a dismiss before any show gives the hold back, a dismiss after the show does not`() = runTest {
        val inApp = InAppStub.getInApp()
        val callbacks = managerWithCapturedCallbacks(inApp)

        callbacks().onInAppDismiss.onDismiss()
        verify(exactly = 1) { inAppMessageInteractor.releaseOverlayShow(inApp.id) }

        val shown = managerWithCapturedCallbacks(inApp)
        shown().onInAppShown.onShown()
        shown().onInAppDismiss.onDismiss()
        // The hold ended with the show (the commit lives in saveShownInApp); nothing to give back.
        verify(exactly = 1) { inAppMessageInteractor.releaseOverlayShow(inApp.id) }
    }

    @Test
    fun `a candidate that found the hold already standing is shown but never gives it back`() = runTest {
        // The hold belongs to the earlier candidate of the same in-app; this one owns nothing.
        val inApp = InAppStub.getInApp()
        val callbacks = managerWithCapturedCallbacks(inApp)
        val secondCandidate = managerWithCapturedCallbacks(inApp, hold = ShowReservationOutcome.ALREADY_HELD)

        secondCandidate().onInAppNotShown.onNotShown()
        secondCandidate().onInAppDismiss.onDismiss()

        verify(exactly = 0) { inAppMessageInteractor.releaseOverlayShow(any()) }
        verify(atLeast = 1) { inAppMessageViewDisplayer.tryShowInAppMessage(any(), any(), any(), any()) }
        callbacks().onInAppNotShown.onNotShown()
        verify(exactly = 1) { inAppMessageInteractor.releaseOverlayShow(inApp.id) }
    }
}
