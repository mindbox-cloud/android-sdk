package cloud.mindbox.mobile_sdk.inapp.presentation.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class WebViewNoCacheRetryPolicyTest {

    private val trackerUrl = "https://api.example.com/scripts/v1/tracker.js?v=1.0.31"

    private fun policy(cacheEnabled: Boolean = true) = WebViewNoCacheRetryPolicy { cacheEnabled }

    @Test
    fun `grants a retry for a script 404 before init`() {
        val policy = policy()

        assertTrue(policy.onHttpError(trackerUrl, 404, hasInitialized = false))
        assertTrue(policy.hasRetried)
        assertEquals("HTTP 404 for $trackerUrl", policy.lastHttpErrorDetail)
    }

    @Test
    fun `grants a retry for a server error on a script`() {
        assertTrue(policy().onHttpError("https://cdn.test/main.js", 503, hasInitialized = false))
    }

    @Test
    fun `grants only one retry per page load`() {
        val policy = policy()

        assertTrue(policy.onHttpError(trackerUrl, 404, hasInitialized = false))
        assertFalse(policy.onHttpError(trackerUrl, 404, hasInitialized = false))
        // The later error still refreshes the telemetry detail.
        assertFalse(policy.onHttpError("https://cdn.test/other.js", 500, hasInitialized = false))
        assertEquals("HTTP 500 for https://cdn.test/other.js", policy.lastHttpErrorDetail)
    }

    @Test
    fun `does not retry after the runtime initialized`() {
        val policy = policy()

        assertFalse(policy.onHttpError(trackerUrl, 404, hasInitialized = true))
        assertFalse(policy.hasRetried)
        // A live in-app must not be reloaded, but the error is still worth remembering.
        assertEquals("HTTP 404 for $trackerUrl", policy.lastHttpErrorDetail)
    }

    @Test
    fun `does not retry non-script resources`() {
        val policy = policy()

        assertFalse(policy.onHttpError("https://cdn.test/banner.png", 404, hasInitialized = false))
        assertFalse(
            policy.onHttpError(
                "https://personalization-speedtest.g.mindbox.ru/client-stats?x=1",
                404,
                hasInitialized = false
            )
        )
        assertNull(policy.lastHttpErrorDetail)
    }

    @Test
    fun `does not retry on statuses below 400 or missing status`() {
        val policy = policy()

        assertFalse(policy.onHttpError(trackerUrl, 302, hasInitialized = false))
        assertFalse(policy.onHttpError(trackerUrl, 200, hasInitialized = false))
        assertFalse(policy.onHttpError(trackerUrl, null, hasInitialized = false))
        assertNull(policy.lastHttpErrorDetail)
        assertFalse(policy.hasRetried)
    }

    @Test
    fun `retries on the boundary status 400`() {
        assertTrue(policy().onHttpError(trackerUrl, 400, hasInitialized = false))
    }

    @Test
    fun `cache feature off blocks the retry but keeps the telemetry detail`() {
        val policy = policy(cacheEnabled = false)

        assertFalse(policy.onHttpError(trackerUrl, 404, hasInitialized = false))
        assertFalse(policy.hasRetried)
        assertEquals("HTTP 404 for $trackerUrl", policy.lastHttpErrorDetail)
    }

    @Test
    fun `cache gate is consulted only when a retry would actually fire`() {
        var consulted = 0
        val policy = WebViewNoCacheRetryPolicy {
            consulted++
            true
        }

        policy.onHttpError("https://cdn.test/banner.png", 404, hasInitialized = false)
        assertEquals(0, consulted)

        policy.onHttpError(trackerUrl, 404, hasInitialized = true)
        assertEquals(0, consulted)

        policy.onHttpError(trackerUrl, 404, hasInitialized = false)
        assertEquals(1, consulted)
    }
}
