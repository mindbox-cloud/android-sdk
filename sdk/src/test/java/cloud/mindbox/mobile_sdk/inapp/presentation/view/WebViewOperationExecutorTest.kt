package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.app.Application
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.MindboxError
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class WebViewOperationExecutorTest {

    private lateinit var executor: MindboxWebViewOperationExecutor

    @Before
    fun onTestStart() {
        executor = MindboxWebViewOperationExecutor()
        mockkObject(MindboxEventManager)
    }

    @After
    fun onTestEnd() {
        unmockkObject(MindboxEventManager)
    }

    @Test
    fun `executeAsyncOperation sends parsed operation and body to event manager`() {
        val context: Application = mockk()
        val payload: String = """{"operation":"OpenScreen","body":{"screen":"home"}}"""
        every { MindboxEventManager.asyncOperation(any(), any(), any()) } returns Unit
        executor.executeAsyncOperation(context, payload)
        verify(exactly = 1) {
            MindboxEventManager.asyncOperation(
                context = context,
                name = "OpenScreen",
                body = """{"screen":"home"}""",
            )
        }
    }

    @Test
    fun `executeAsyncOperation throws when payload misses operation`() {
        val context: Application = mockk()
        val payload: String = """{"body":{"screen":"home"}}"""
        try {
            executor.executeAsyncOperation(context, payload)
            fail("Expected IllegalArgumentException")
        } catch (exception: IllegalArgumentException) {
            assertEquals("Operation is not provided", exception.message)
        }
        verify(exactly = 0) { MindboxEventManager.asyncOperation(any(), any(), any()) }
    }

    @Test
    fun `executeAsyncOperation throws when payload is invalid json empty or null`() {
        val context: Application = mockk()
        val payloads: List<String?> = listOf("not-json", "", null)
        payloads.forEach { payload: String? ->
            try {
                executor.executeAsyncOperation(context, payload)
                fail("Expected exception for payload: $payload")
            } catch (exception: Exception) {
                // Expected: payload cannot be parsed to required JSON object.
            }
        }
        verify(exactly = 0) { MindboxEventManager.asyncOperation(any(), any(), any()) }
    }

    @Test
    fun `executeSyncOperation returns response when event manager succeeds`() = runTest {
        val payload: String = """{"operation":"OpenScreen","body":{"screen":"home"}}"""
        val expectedResponse: String = """{"result":"ok"}"""
        every {
            MindboxEventManager.syncOperation(
                name = any(),
                bodyJson = any(),
                onSuccess = any(),
                onError = any(),
            )
        } answers {
            val onSuccess: (String) -> Unit = arg(2)
            onSuccess(expectedResponse)
        }
        val actualResponse: String = executor.executeSyncOperation(payload)
        assertEquals(expectedResponse, actualResponse)
        verify(exactly = 1) {
            MindboxEventManager.syncOperation(
                name = "OpenScreen",
                bodyJson = """{"screen":"home"}""",
                onSuccess = any(),
                onError = any(),
            )
        }
    }

    @Test
    fun `executeSyncOperation throws IllegalStateException when event manager returns error`() = runTest {
        val payload: String = """{"operation":"OpenScreen","body":{"screen":"home"}}"""
        val expectedError: MindboxError = MindboxError.Unknown(Throwable("network failure"))
        every {
            MindboxEventManager.syncOperation(
                name = any(),
                bodyJson = any(),
                onSuccess = any(),
                onError = any(),
            )
        } answers {
            val onError: (MindboxError) -> Unit = arg(3)
            onError(expectedError)
        }
        try {
            executor.executeSyncOperation(payload)
            fail("Expected IllegalStateException")
        } catch (exception: IllegalStateException) {
            assertEquals(expectedError.toJson(), exception.message)
        }
    }

    @Test
    fun `executeSyncOperation throws when payload misses body`() = runTest {
        val payload: String = """{"operation":"OpenScreen"}"""
        try {
            executor.executeSyncOperation(payload)
            fail("Expected IllegalArgumentException")
        } catch (exception: IllegalArgumentException) {
            assertEquals("Body is not provided", exception.message)
        }
        verify(exactly = 0) {
            MindboxEventManager.syncOperation(
                name = any(),
                bodyJson = any(),
                onSuccess = any(),
                onError = any(),
            )
        }
    }

    @Test
    fun `executeSyncOperation throws when payload is invalid json empty or null`() = runTest {
        val payloads: List<String?> = listOf("not-json", "", null)
        payloads.forEach { payload: String? ->
            try {
                executor.executeSyncOperation(payload)
                fail("Expected exception for payload: $payload")
            } catch (exception: Exception) {
                // Expected: payload cannot be parsed to required JSON object.
            }
        }
        verify(exactly = 0) {
            MindboxEventManager.syncOperation(
                name = any(),
                bodyJson = any(),
                onSuccess = any(),
                onError = any(),
            )
        }
    }
}
