package cloud.mindbox.mobile_sdk.network

import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import com.android.volley.Header
import com.android.volley.NetworkResponse
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebViewContentRequestTest {

    private val url = "https://cdn.example/stories.html"

    private val request = WebViewContentRequest(url = url, listener = {}, errorListener = {})

    @Before
    fun setUp() {
        mockkObject(MindboxLoggerImpl)
    }

    @After
    fun tearDown() {
        unmockkObject(MindboxLoggerImpl)
    }

    @Test
    fun `a successful answer is logged at debug with status, time, size and cache headers`() {
        val response = NetworkResponse(
            200,
            "<html>".toByteArray(),
            false,
            143L,
            listOf(Header("Age", "0"), Header("X-Cache", "HIT"), Header("ETag", "abc")),
        )

        val parsed = request.parseNetworkResponse(response)

        assertTrue(parsed.isSuccess)
        assertEquals("<html>", parsed.result)
        verify(exactly = 1) {
            MindboxLoggerImpl.d(any(), "<--- 200 $url in 143 ms, 6 bytes [Age: 0, X-Cache: HIT]")
        }
    }

    @Test
    fun `without cache headers the line has no bracket part`() {
        val response = NetworkResponse(304, ByteArray(0), true, 12L, emptyList())

        request.parseNetworkResponse(response)

        verify(exactly = 1) { MindboxLoggerImpl.d(any(), "<--- 304 $url in 12 ms, 0 bytes") }
    }

    @Test
    fun `the page is never served from the volley cache`() {
        assertFalse(request.shouldCache())
    }
}
