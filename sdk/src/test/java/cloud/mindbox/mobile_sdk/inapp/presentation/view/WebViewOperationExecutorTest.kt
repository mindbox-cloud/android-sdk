package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.app.Application
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.MindboxError
import com.google.gson.Gson
import io.mockk.*
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
        executor = MindboxWebViewOperationExecutor(Gson())
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
        executor.executeAsyncOperation(context, payload, tags = null)
        verify(exactly = 1) {
            MindboxEventManager.asyncOperation(
                context = context,
                name = "OpenScreen",
                body = """{"screen":"home"}""",
            )
        }
    }

    @Test
    fun `executeAsyncOperation adds top-level tags to body when tags present`() {
        val context: Application = mockk()
        val payload: String = """{"operation":"OpenScreen","body":{"screen":"home"}}"""
        every { MindboxEventManager.asyncOperation(any(), any(), any()) } returns Unit
        executor.executeAsyncOperation(context, payload, tags = mapOf("templateType" to "Popup"))
        verify(exactly = 1) {
            MindboxEventManager.asyncOperation(
                context = context,
                name = "OpenScreen",
                body = """{"screen":"home","tags":{"templateType":"Popup"}}""",
            )
        }
    }

    @Test
    fun `executeAsyncOperation does not add tags when tags empty`() {
        val context: Application = mockk()
        val payload: String = """{"operation":"OpenScreen","body":{"screen":"home"}}"""
        every { MindboxEventManager.asyncOperation(any(), any(), any()) } returns Unit
        executor.executeAsyncOperation(context, payload, tags = emptyMap())
        verify(exactly = 1) {
            MindboxEventManager.asyncOperation(
                context = context,
                name = "OpenScreen",
                body = """{"screen":"home"}""",
            )
        }
    }

    @Test
    fun `executeAsyncOperation adds in-app tags when existing tags is json null`() {
        val context: Application = mockk()
        val payload: String = """{"operation":"OpenScreen","body":{"screen":"home","tags":null}}"""
        every { MindboxEventManager.asyncOperation(any(), any(), any()) } returns Unit
        executor.executeAsyncOperation(context, payload, tags = mapOf("templateType" to "Popup"))
        verify(exactly = 1) {
            MindboxEventManager.asyncOperation(
                context = context,
                name = "OpenScreen",
                body = """{"screen":"home","tags":{"templateType":"Popup"}}""",
            )
        }
    }

    @Test
    fun `executeAsyncOperation merges in-app tags into existing tags without collision`() {
        val context: Application = mockk()
        val payload: String = """{"operation":"OpenScreen","body":{"screen":"home","tags":{"client":"own"}}}"""
        every { MindboxEventManager.asyncOperation(any(), any(), any()) } returns Unit
        executor.executeAsyncOperation(context, payload, tags = mapOf("templateType" to "Popup"))
        verify(exactly = 1) {
            MindboxEventManager.asyncOperation(
                context = context,
                name = "OpenScreen",
                body = """{"screen":"home","tags":{"client":"own","templateType":"Popup"}}""",
            )
        }
    }

    @Test
    fun `executeAsyncOperation keeps client value and skips in-app value on key collision`() {
        val context: Application = mockk()
        val payload: String = """{"operation":"OpenScreen","body":{"screen":"home","tags":{"templateType":"ClientOwn"}}}"""
        every { MindboxEventManager.asyncOperation(any(), any(), any()) } returns Unit
        executor.executeAsyncOperation(context, payload, tags = mapOf("templateType" to "Popup"))
        verify(exactly = 1) {
            MindboxEventManager.asyncOperation(
                context = context,
                name = "OpenScreen",
                body = """{"screen":"home","tags":{"templateType":"ClientOwn"}}""",
            )
        }
    }

    @Test
    fun `executeAsyncOperation merges only non-colliding keys when tags partially overlap`() {
        val context: Application = mockk()
        val payload: String =
            """{"operation":"OpenScreen","body":{"screen":"home","tags":{"templateType":"ClientOwn","keep":"x"}}}"""
        every { MindboxEventManager.asyncOperation(any(), any(), any()) } returns Unit
        executor.executeAsyncOperation(
            context,
            payload,
            tags = mapOf("templateType" to "Popup", "campaign" to "summer"),
        )
        verify(exactly = 1) {
            MindboxEventManager.asyncOperation(
                context = context,
                name = "OpenScreen",
                body = """{"screen":"home","tags":{"templateType":"ClientOwn","keep":"x","campaign":"summer"}}""",
            )
        }
    }

    @Test
    fun `executeAsyncOperation keeps client tags untouched when existing tags is not an object`() {
        val context: Application = mockk()
        val payload: String = """{"operation":"OpenScreen","body":{"screen":"home","tags":"raw"}}"""
        every { MindboxEventManager.asyncOperation(any(), any(), any()) } returns Unit
        executor.executeAsyncOperation(context, payload, tags = mapOf("templateType" to "Popup"))
        verify(exactly = 1) {
            MindboxEventManager.asyncOperation(
                context = context,
                name = "OpenScreen",
                body = """{"screen":"home","tags":"raw"}""",
            )
        }
    }

    @Test
    fun `executeAsyncOperation throws when payload misses operation`() {
        val context: Application = mockk()
        val payload: String = """{"body":{"screen":"home"}}"""
        try {
            executor.executeAsyncOperation(context, payload, tags = null)
            fail("Expected IllegalArgumentException")
        } catch (exception: IllegalArgumentException) {
            assertEquals("Operation is not provided", exception.message)
        }
        verify(exactly = 0) { MindboxEventManager.asyncOperation(any(), any(), any()) }
    }

    @Test
    fun `executeAsyncOperation throws when payload misses body`() {
        val context: Application = mockk()
        val payload: String = """{"operation":"OpenScreen"}"""
        try {
            executor.executeAsyncOperation(context, payload, tags = null)
            fail("Expected IllegalArgumentException")
        } catch (exception: IllegalArgumentException) {
            assertEquals("Body is not provided", exception.message)
        }
        verify(exactly = 0) { MindboxEventManager.asyncOperation(any(), any(), any()) }
    }

    @Test
    fun `executeAsyncOperation throws IllegalArgumentException when payload is null`() {
        val context: Application = mockk()
        try {
            executor.executeAsyncOperation(context, payload = null, tags = null)
            fail("Expected IllegalArgumentException")
        } catch (exception: IllegalArgumentException) {
            assertEquals("Payload is not provided", exception.message)
        }
        verify(exactly = 0) { MindboxEventManager.asyncOperation(any(), any(), any()) }
    }

    @Test
    fun `executeAsyncOperation throws IllegalArgumentException when payload is invalid json`() {
        val context: Application = mockk()
        val payloads: List<String> = listOf("not-json", "")
        payloads.forEach { payload: String ->
            try {
                executor.executeAsyncOperation(context, payload, tags = null)
                fail("Expected IllegalArgumentException for payload: $payload")
            } catch (exception: IllegalArgumentException) {
                assertEquals("Payload is not a valid JSON object", exception.message)
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
        val actualResponse: String = executor.executeSyncOperation(payload, tags = null)
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
    fun `executeSyncOperation adds top-level tags and still returns response`() = runTest {
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
        val actualResponse: String = executor.executeSyncOperation(payload, tags = mapOf("templateType" to "Popup"))
        assertEquals(expectedResponse, actualResponse)
        verify(exactly = 1) {
            MindboxEventManager.syncOperation(
                name = "OpenScreen",
                bodyJson = """{"screen":"home","tags":{"templateType":"Popup"}}""",
                onSuccess = any(),
                onError = any(),
            )
        }
    }

    @Test
    fun `executeSyncOperation merges tags keeping client value on collision and still returns response`() = runTest {
        val payload: String =
            """{"operation":"OpenScreen","body":{"screen":"home","tags":{"templateType":"ClientOwn"}}}"""
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
        val actualResponse: String = executor.executeSyncOperation(
            payload,
            tags = mapOf("templateType" to "Popup", "campaign" to "summer"),
        )
        assertEquals(expectedResponse, actualResponse)
        verify(exactly = 1) {
            MindboxEventManager.syncOperation(
                name = "OpenScreen",
                bodyJson = """{"screen":"home","tags":{"templateType":"ClientOwn","campaign":"summer"}}""",
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
            executor.executeSyncOperation(payload, tags = null)
            fail("Expected IllegalStateException")
        } catch (exception: IllegalStateException) {
            assertEquals(expectedError.toJson(), exception.message)
        }
    }

    @Test
    fun `executeSyncOperation throws when payload misses body`() = runTest {
        val payload: String = """{"operation":"OpenScreen"}"""
        try {
            executor.executeSyncOperation(payload, tags = null)
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
    fun `executeSyncOperation throws IllegalArgumentException when payload is null`() = runTest {
        try {
            executor.executeSyncOperation(payload = null, tags = null)
            fail("Expected IllegalArgumentException")
        } catch (exception: IllegalArgumentException) {
            assertEquals("Payload is not provided", exception.message)
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
    fun `executeSyncOperation throws IllegalArgumentException when payload is invalid json`() = runTest {
        val payloads: List<String> = listOf("not-json", "")
        payloads.forEach { payload: String ->
            try {
                executor.executeSyncOperation(payload, tags = null)
                fail("Expected IllegalArgumentException for payload: $payload")
            } catch (exception: IllegalArgumentException) {
                assertEquals("Payload is not a valid JSON object", exception.message)
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
