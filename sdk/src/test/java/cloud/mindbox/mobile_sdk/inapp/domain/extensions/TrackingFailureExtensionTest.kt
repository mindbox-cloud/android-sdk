package cloud.mindbox.mobile_sdk.inapp.domain.extensions

import cloud.mindbox.mobile_sdk.inapp.domain.models.CustomerSegmentationError
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoError
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppContentFetchingError
import cloud.mindbox.mobile_sdk.inapp.domain.models.ProductSegmentationError
import com.android.volley.NetworkResponse
import com.android.volley.NoConnectionError
import com.android.volley.TimeoutError
import com.android.volley.VolleyError
import com.bumptech.glide.load.HttpException
import com.bumptech.glide.load.engine.GlideException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal class TrackingFailureExtensionTest {

    @Test
    fun `shouldTrackTargetingError returns true for 5xx server error`() {
        val serverError = VolleyError(NetworkResponse(500, null, false, 0, emptyList()))
        val geoError = GeoError(serverError)
        assertTrue(geoError.shouldTrackTargetingError())
    }

    @Test
    fun `shouldTrackTargetingError returns true for 503 server error`() {
        val serverError = VolleyError(NetworkResponse(503, null, false, 0, emptyList()))
        val segmentationError = CustomerSegmentationError(serverError)
        assertTrue(segmentationError.shouldTrackTargetingError())
    }

    @Test
    fun `shouldTrackTargetingError returns false for TimeoutError`() {
        val timeoutError = TimeoutError()
        val geoError = GeoError(timeoutError)
        assertFalse(geoError.shouldTrackTargetingError())
    }

    @Test
    fun `shouldTrackTargetingError returns false for NoConnectionError`() {
        val noConnectionError = NoConnectionError()
        val segmentationError = CustomerSegmentationError(noConnectionError)
        assertFalse(segmentationError.shouldTrackTargetingError())
    }

    @Test
    fun `shouldTrackTargetingError returns false for VolleyError with SocketTimeoutException cause`() {
        val volleyError = VolleyError(SocketTimeoutException("timeout"))
        val productError = ProductSegmentationError(volleyError)
        assertFalse(productError.shouldTrackTargetingError())
    }

    @Test
    fun `shouldTrackTargetingError returns false for 4xx client error`() {
        val clientError = VolleyError(NetworkResponse(404, null, false, 0, emptyList()))
        val geoError = GeoError(clientError)
        assertFalse(geoError.shouldTrackTargetingError())
    }

    @Test
    fun `shouldTrackTargetingError returns false when cause is not VolleyError`() {
        val throwable = Exception(IllegalStateException("not volley"))
        assertFalse(throwable.shouldTrackTargetingError())
    }

    @Test
    fun `shouldTrackImageDownloadError returns false for GlideException with SocketTimeoutException in rootCauses`() {
        val glideException = GlideException("load failed", listOf(SocketTimeoutException("timeout")))
        val inAppError = InAppContentFetchingError(glideException)
        assertFalse(inAppError.shouldTrackImageDownloadError())
    }

    @Test
    fun `shouldTrackImageDownloadError returns false for GlideException with HttpException and UnknownHostException`() {
        val httpException = HttpException("connection failed", -1, UnknownHostException("no host"))
        val glideException = GlideException("load failed", listOf(httpException))
        val inAppError = InAppContentFetchingError(glideException)
        assertFalse(inAppError.shouldTrackImageDownloadError())
    }

    @Test
    fun `shouldTrackImageDownloadError returns false for GlideException with HttpException and ConnectException`() {
        val httpException = HttpException("connection failed", -1, ConnectException("connection refused"))
        val glideException = GlideException("load failed", listOf(httpException))
        val inAppError = InAppContentFetchingError(glideException)
        assertFalse(inAppError.shouldTrackImageDownloadError())
    }

    @Test
    fun `shouldTrackImageDownloadError returns true for GlideException with 404 HttpException`() {
        val httpException = HttpException("not found", 404)
        val glideException = GlideException("load failed", listOf(httpException))
        val inAppError = InAppContentFetchingError(glideException)
        assertTrue(inAppError.shouldTrackImageDownloadError())
    }

    @Test
    fun `shouldTrackImageDownloadError returns true for GlideException with 500 HttpException`() {
        val httpException = HttpException("server error", 500)
        val glideException = GlideException("load failed", listOf(httpException))
        val inAppError = InAppContentFetchingError(glideException)
        assertTrue(inAppError.shouldTrackImageDownloadError())
    }

    @Test
    fun `shouldTrackImageDownloadError returns true when cause is not GlideException`() {
        val throwable = Exception("generic error")
        assertTrue(throwable.shouldTrackImageDownloadError())
    }

    @Test
    fun `shouldTrackImageDownloadError returns false for GlideException with SocketTimeoutException as cause of rootCause`() {
        val rootCause = Exception(SocketTimeoutException("timeout"))
        val glideException = GlideException("load failed", listOf(rootCause))
        val inAppError = InAppContentFetchingError(glideException)
        assertFalse(inAppError.shouldTrackImageDownloadError())
    }
}
