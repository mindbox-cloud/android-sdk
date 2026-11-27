package cloud.mindbox.mobile_sdk.network

import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.models.MindboxRequest
import cloud.mindbox.mobile_sdk.utils.setMindboxScope
import com.android.volley.RequestQueue
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MindboxServiceGeneratorTest {

    private val originalMindboxScope = Mindbox.mindboxScope
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setUp() {
        mockkObject(MindboxDI)
        every { MindboxDI.appModule } returns mockk(relaxed = true)
        setMindboxScope(scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        setMindboxScope(originalMindboxScope)
        unmockkAll()
    }

    @Test
    fun `stopped SDK scope cancels queued requests except page loads and push delivery confirmations`() {
        val filter = slot<RequestQueue.RequestFilter>()
        val requestQueue = mockk<RequestQueue>(relaxed = true) {
            every { cancelAll(capture(filter)) } just runs
        }
        MindboxServiceGenerator(requestQueue)

        scope.cancel()

        assertFalse(filter.captured.apply(mockk<PushDeliveredRequest>()))
        assertFalse(filter.captured.apply(mockk<WebViewContentRequest>()))
        assertTrue(filter.captured.apply(mockk<MindboxRequest>()))
    }
}
