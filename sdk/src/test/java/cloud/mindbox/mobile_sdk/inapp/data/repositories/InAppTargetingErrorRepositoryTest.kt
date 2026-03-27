package cloud.mindbox.mobile_sdk.inapp.data.repositories

import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.models.CustomerSegmentationError
import cloud.mindbox.mobile_sdk.inapp.domain.models.GeoError
import cloud.mindbox.mobile_sdk.inapp.domain.models.ProductSegmentationError
import cloud.mindbox.mobile_sdk.inapp.domain.models.TargetingErrorKey
import com.android.volley.NetworkResponse
import com.android.volley.VolleyError
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

internal class InAppTargetingErrorRepositoryTest {
    private val sessionStorageManager = mockk<SessionStorageManager>(relaxUnitFun = true)
    private val repository = InAppTargetingErrorRepositoryImpl(sessionStorageManager)

    @Test
    fun `saveError stores customer segmentation error`() {
        val errors = mutableMapOf<TargetingErrorKey, String>()
        val responseBody = """{"error":"customer segmentation failed"}"""
        val volleyError = createVolleyError(statusCode = 500, responseBody = responseBody, networkTimeMs = 100)
        val throwable = CustomerSegmentationError(volleyError)
        every { sessionStorageManager.lastTargetingErrors } returns errors
        repository.saveError(TargetingErrorKey.CustomerSegmentation, throwable)
        val expectedDetails = "statusCode=500, networkTimeMs=${volleyError.networkTimeMs}, body=$responseBody"
        assertEquals("${throwable.message}. $expectedDetails", errors[TargetingErrorKey.CustomerSegmentation])
    }

    @Test
    fun `saveError stores geo error`() {
        val errors = mutableMapOf<TargetingErrorKey, String>()
        val responseBody = """{"error":"geo failed"}"""
        val volleyError = createVolleyError(statusCode = 503, responseBody = responseBody, networkTimeMs = 200)
        val throwable = GeoError(volleyError)
        every { sessionStorageManager.lastTargetingErrors } returns errors
        repository.saveError(TargetingErrorKey.Geo, throwable)
        val expectedDetails = "statusCode=503, networkTimeMs=${volleyError.networkTimeMs}, body=$responseBody"
        assertEquals("${throwable.message}. $expectedDetails", errors[TargetingErrorKey.Geo])
    }

    @Test
    fun `saveError stores product segmentation error`() {
        val product = "website" to "ProductRandomName"
        val productKey = TargetingErrorKey.ProductSegmentation(product)
        val errors = mutableMapOf<TargetingErrorKey, String>()
        val responseBody = """{"error":"product segmentation failed"}"""
        val volleyError = createVolleyError(statusCode = 504, responseBody = responseBody, networkTimeMs = 300)
        val throwable = ProductSegmentationError(volleyError)
        every { sessionStorageManager.lastTargetingErrors } returns errors
        repository.saveError(productKey, throwable)
        val expectedDetails = "statusCode=504, networkTimeMs=${volleyError.networkTimeMs}, body=$responseBody"
        assertEquals("${throwable.message}. $expectedDetails", errors[productKey])
    }

    @Test
    fun `getError returns saved error`() {
        val product = "website" to "ProductRandomName"
        val productKey = TargetingErrorKey.ProductSegmentation(product)
        val errorDetails = "Product segmentation fetch failed"
        every { sessionStorageManager.lastTargetingErrors[productKey] } returns errorDetails
        val result = repository.getError(productKey)
        assertEquals(errorDetails, result)
    }

    @Test
    fun `getError returns null when no error saved`() {
        every { sessionStorageManager.lastTargetingErrors[TargetingErrorKey.Geo] } returns null
        val result = repository.getError(TargetingErrorKey.Geo)
        assertEquals(null, result)
    }

    @Test
    fun `clearErrors clears all stored errors`() {
        val errors = mutableMapOf<TargetingErrorKey, String>(
            TargetingErrorKey.Geo to "Geo error",
            TargetingErrorKey.CustomerSegmentation to "Customer error"
        )
        every { sessionStorageManager.lastTargetingErrors } returns errors
        repository.clearErrors()
        assertEquals(emptyMap<TargetingErrorKey, String>(), errors)
    }

    private fun createVolleyError(
        statusCode: Int,
        responseBody: String,
        networkTimeMs: Long,
    ): VolleyError {
        val response = NetworkResponse(statusCode, responseBody.toByteArray(), false, networkTimeMs, emptyList())
        return VolleyError(response)
    }
}
