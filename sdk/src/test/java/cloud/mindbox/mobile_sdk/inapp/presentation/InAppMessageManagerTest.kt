package cloud.mindbox.mobile_sdk.inapp.presentation

import app.cash.turbine.test
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.inapp.domain.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.InAppMessageViewDisplayer
import cloud.mindbox.mobile_sdk.inapp.domain.InAppType
import com.android.volley.VolleyError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*

@RunWith(MockitoJUnitRunner::class)
internal class InAppMessageManagerTest {

    @Mock
    private lateinit var mindboxConfiguration: MindboxConfiguration

    @Mock
    private lateinit var inAppMessageInteractor: InAppInteractor

    @Mock
    private lateinit var inAppMessageViewDisplayer: InAppMessageViewDisplayer

    @InjectMocks
    private lateinit var inAppMessageManager: InAppMessageManagerImpl

    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    @Before
    fun onTestStart() {
        runBlocking {
            Dispatchers.setMain(mainThreadSurrogate)
            whenever(inAppMessageViewDisplayer.showInAppMessage(any(), any(), any())).thenReturn(
                Unit)
        }
    }

    @After
    fun onTestFinish() {
        Dispatchers.resetMain()
        mainThreadSurrogate.close()
    }

    @Test
    fun `in app config is being fetched`() {
        inAppMessageManager.requestConfig(mindboxConfiguration)
        runBlocking {
            verify(inAppMessageInteractor, times(1)).fetchInAppConfig(mindboxConfiguration)
        }
    }

    @Test
    fun `in app messages success message`() {
        whenever(inAppMessageInteractor.processEventAndConfig(mindboxConfiguration)).thenReturn(flow {
            emit(InAppType.SimpleImage(inAppId = "123",
                imageUrl = "",
                redirectUrl = "",
                intentData = ""))
        })
        inAppMessageManager.listenEventAndInApp(mindboxConfiguration)
        runBlocking {
            inAppMessageInteractor.processEventAndConfig(mindboxConfiguration).test {
                awaitItem()
                verify(inAppMessageViewDisplayer, times(1)).showInAppMessage(any(), any(), any())
                awaitComplete()
            }
        }
    }

    @Test
    fun `in app messages error message`() {
        whenever(inAppMessageInteractor.processEventAndConfig(mindboxConfiguration)).thenAnswer {
            flow<InAppType> {
                error("abc")
            }
        }
        inAppMessageManager.listenEventAndInApp(mindboxConfiguration)
        runBlocking {
            inAppMessageInteractor.processEventAndConfig(mindboxConfiguration).test {
                awaitError()
            }
        }


    }

    @Test
    fun `fetch config network error`() {
        runBlocking {
            whenever(inAppMessageInteractor.fetchInAppConfig(mindboxConfiguration)).thenAnswer {
                flow<InAppType> {
                    throw VolleyError()
                }
            }
            inAppMessageManager.listenEventAndInApp(mindboxConfiguration);
            {
                runBlocking {
                    inAppMessageInteractor.fetchInAppConfig(mindboxConfiguration)
                }
            }
                .shouldNotThrow()
        }
    }

    private fun (() -> Any?).shouldNotThrow() = try {
        invoke()
    } catch (ex: Exception) {
        throw Error("expected not to throw!", ex)
    }


    @Test
    fun `fetch config non network error`() {
        runBlocking {
            whenever(inAppMessageInteractor.fetchInAppConfig(mindboxConfiguration)).thenAnswer {
                flow<InAppType> {
                    error("")
                }
            }
            inAppMessageManager.listenEventAndInApp(mindboxConfiguration);
            { runBlocking { inAppMessageInteractor.fetchInAppConfig(mindboxConfiguration) } }
                .shouldNotThrow()
        }
    }
}