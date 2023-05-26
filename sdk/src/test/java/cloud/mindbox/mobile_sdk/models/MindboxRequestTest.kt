package cloud.mindbox.mobile_sdk.models

import cloud.mindbox.mobile_sdk.utils.Constants
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MindboxRequestTest {
    @Test
    fun `mindboxRequest not contains sdkVersionNumeric`() {
        val request = MindboxRequest(mockk(relaxed = true))
        assertFalse(request.headers.contains(MindboxRequest.HEADER_SDK_VERSION_NUMERIC))
    }

    @Test
    fun `tackVisit mindboxRequest contains sdkVersionNumeric`() {
        val request = MindboxTrackVisitRequest(mockk(relaxed = true))
        assertEquals(Constants.SDK_VERSION_NUMERIC.toString(), request.headers[MindboxRequest.HEADER_SDK_VERSION_NUMERIC])
    }
}