package cloud.mindbox.mobile_sdk.inapp.presentation

import app.cash.turbine.test
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.inapp.domain.InAppInteractorImpl
import cloud.mindbox.mobile_sdk.inapp.domain.InAppType
import com.android.volley.VolleyError
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner::class)
internal class InAppMessageManagerTest {

    @Mock
    private lateinit var mindboxConfiguration: MindboxConfiguration

    @Mock
    private lateinit var inAppMessageInteractor: InAppInteractorImpl

    @Test
    fun `in app messages success message`() {
        whenever(inAppMessageInteractor.processEventAndConfig(mindboxConfiguration)).thenReturn(flow {
            emit(InAppType.SimpleImage(inAppId = "123",
                imageUrl = "",
                redirectUrl = "",
                intentData = ""))
        })

        runBlocking {
            inAppMessageInteractor.processEventAndConfig(mindboxConfiguration).test {
                assertNotNull(awaitItem())
                awaitComplete()
            }
        }
    }

    @Test
    fun `in app messages error message`() {
        whenever(inAppMessageInteractor.processEventAndConfig(mindboxConfiguration)).thenAnswer {
            throw Error()
        }
        assertThrows(Error::class.java) {
            runBlocking {
                inAppMessageInteractor.processEventAndConfig(mindboxConfiguration).test {
                }
            }
        }

    }

    @Test
    fun `fetch config network error`() {
        runBlocking {
            whenever(inAppMessageInteractor.fetchInAppConfig(mindboxConfiguration)).thenAnswer {
                throw VolleyError()
            }
            assertThrows(VolleyError::class.java) {
                runBlocking {
                    inAppMessageInteractor.fetchInAppConfig(mindboxConfiguration)
                }
            }
        }
    }

    @Test
    fun `fetch config non network error`() {
        runBlocking {
            whenever(inAppMessageInteractor.fetchInAppConfig(mindboxConfiguration)).thenAnswer {
                throw Error()
            }
            assertThrows(Error::class.java) {
                runBlocking {
                    inAppMessageInteractor.fetchInAppConfig(mindboxConfiguration)
                }
            }
        }
    }


}