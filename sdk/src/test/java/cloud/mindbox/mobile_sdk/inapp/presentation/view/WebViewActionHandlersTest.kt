package cloud.mindbox.mobile_sdk.inapp.presentation.view

import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, InternalMindboxApi::class)
class WebViewActionHandlersTest {

    @Test
    fun `handleRequest returns payload from registered handler`() {
        val handlers: WebViewActionHandlers = WebViewActionHandlers()
        val expectedPayload: String = "payload"
        val message: BridgeMessage.Request = createRequest(WebViewAction.INIT)
        handlers.register(WebViewAction.INIT) { expectedPayload }
        val actualResult: Result<String> = handlers.handleRequest(message)
        assertTrue(actualResult.isSuccess)
        assertEquals(expectedPayload, actualResult.getOrNull())
    }

    @Test
    fun `handleRequest returns failure when handler not registered`() {
        val handlers: WebViewActionHandlers = WebViewActionHandlers()
        val message: BridgeMessage.Request = createRequest(WebViewAction.INIT)
        val actualResult: Result<String> = handlers.handleRequest(message)
        assertTrue(actualResult.isFailure)
    }

    @Test
    fun `handleRequestSuspend returns payload from registered suspend handler`() = runTest {
        val handlers: WebViewActionHandlers = WebViewActionHandlers()
        val expectedPayload: String = "payload"
        val message: BridgeMessage.Request = createRequest(WebViewAction.READY)
        handlers.registerSuspend(WebViewAction.READY) { expectedPayload }
        val actualResult: Result<String> = handlers.handleRequestSuspend(message)
        assertTrue(actualResult.isSuccess)
        assertEquals(expectedPayload, actualResult.getOrNull())
    }

    @Test
    fun `handleRequestSuspend returns failure when suspend handler not registered`() = runTest {
        val handlers: WebViewActionHandlers = WebViewActionHandlers()
        val message: BridgeMessage.Request = createRequest(WebViewAction.READY)
        val actualResult: Result<String> = handlers.handleRequestSuspend(message)
        assertTrue(actualResult.isFailure)
    }

    @Test
    fun `hasSuspendHandler returns true when handler registered`() {
        val handlers: WebViewActionHandlers = WebViewActionHandlers()
        handlers.registerSuspend(WebViewAction.READY) { BridgeMessage.EMPTY_PAYLOAD }
        val actualResult: Boolean = handlers.hasSuspendHandler(WebViewAction.READY)
        assertTrue(actualResult)
    }

    @Test
    fun `hasSuspendHandler returns false when handler not registered`() {
        val handlers: WebViewActionHandlers = WebViewActionHandlers()
        val actualResult: Boolean = handlers.hasSuspendHandler(WebViewAction.READY)
        assertFalse(actualResult)
    }

    @Test
    fun `handleRequestSuspend completes after delay`() = runTest {
        val handlers: WebViewActionHandlers = WebViewActionHandlers()
        val expectedPayload: String = "delayed"
        val message: BridgeMessage.Request = createRequest(WebViewAction.READY)
        handlers.registerSuspend(WebViewAction.READY) {
            delay(100)
            expectedPayload
        }
        val dispatcher: TestDispatcher = StandardTestDispatcher(testScheduler)
        val deferredResult: Deferred<Result<String>> =
            async(dispatcher) { handlers.handleRequestSuspend(message) }
        runCurrent()
        assertFalse(deferredResult.isCompleted)
        advanceTimeBy(99)
        runCurrent()
        assertFalse(deferredResult.isCompleted)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(deferredResult.isCompleted)
        assertEquals(expectedPayload, deferredResult.await().getOrNull())
    }

    @Test
    fun `handleRequestSuspend processes multiple requests with different delays`() = runTest {
        val handlers: WebViewActionHandlers = WebViewActionHandlers()
        val firstPayload: String = "first"
        val secondPayload: String = "second"
        val firstMessage: BridgeMessage.Request = createRequest(WebViewAction.READY)
        val secondMessage: BridgeMessage.Request = createRequest(WebViewAction.INIT)
        handlers.registerSuspend(WebViewAction.READY) {
            delay(50)
            firstPayload
        }
        handlers.registerSuspend(WebViewAction.INIT) {
            delay(150)
            secondPayload
        }
        val dispatcher: TestDispatcher = StandardTestDispatcher(testScheduler)
        val firstDeferred: Deferred<Result<String>> =
            async(dispatcher) { handlers.handleRequestSuspend(firstMessage) }
        val secondDeferred: Deferred<Result<String>> =
            async(dispatcher) { handlers.handleRequestSuspend(secondMessage) }
        runCurrent()
        assertFalse(firstDeferred.isCompleted)
        assertFalse(secondDeferred.isCompleted)
        advanceTimeBy(50)
        runCurrent()
        assertTrue(firstDeferred.isCompleted)
        assertFalse(secondDeferred.isCompleted)
        advanceTimeBy(100)
        runCurrent()
        assertTrue(secondDeferred.isCompleted)
        assertEquals(firstPayload, firstDeferred.await().getOrNull())
        assertEquals(secondPayload, secondDeferred.await().getOrNull())
    }

    private fun createRequest(action: WebViewAction): BridgeMessage.Request {
        return BridgeMessage.Request(
            version = BridgeMessage.VERSION,
            action = action,
            payload = BridgeMessage.EMPTY_PAYLOAD,
            id = "request-id",
            timestamp = 1L,
        )
    }
}
