package cloud.mindbox.mobile_sdk.inapp.presentation.view

import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dispatch rule every surface shares: one envelope per request, presence read before any
 * handler runs, and an action the surface does not serve answered by that action's own rule.
 */
@OptIn(ExperimentalCoroutinesApi::class, InternalMindboxApi::class)
class WebViewActionHandlersTest {

    private var responded: String? = null
    private var refused: Throwable? = null

    private fun WebViewActionHandlers.dispatchInTest(
        action: WebViewAction,
        isUserPresent: Boolean = true,
        isAlive: () -> Boolean = { true },
    ) = dispatch(
        message = createRequest(action),
        isUserPresent = isUserPresent,
        isAlive = isAlive,
        launchSuspending = { handle -> runBlocking { handle() } },
        respond = { payload -> responded = payload },
        refuse = { error -> refused = error },
    )

    @Test
    fun `a blocking handler answers with its own payload`() {
        val handlers = WebViewActionHandlers()
        handlers.register(WebViewAction.INIT) { "payload" }

        handlers.dispatchInTest(WebViewAction.INIT)

        assertEquals("payload", responded)
        assertNull(refused)
    }

    @Test
    fun `a suspending handler answers through the launcher it was given`() {
        val handlers = WebViewActionHandlers()
        handlers.registerSuspend(WebViewAction.READY) {
            delay(10)
            "ready-payload"
        }

        handlers.dispatchInTest(WebViewAction.READY)

        assertEquals("ready-payload", responded)
    }

    @Test
    fun `an action registered both ways is served by its suspending handler`() {
        val handlers = WebViewActionHandlers()
        handlers.register(WebViewAction.READY) { "blocking" }
        handlers.registerSuspend(WebViewAction.READY) { "suspending" }

        handlers.dispatchInTest(WebViewAction.READY)

        assertEquals("suspending", responded)
    }

    @Test
    fun `an action the surface does not perform is acknowledged`() {
        // A block has no window to close and nothing to click: "nothing to do here" is an outcome
        // the page may carry on from, not a failure.
        val handlers = WebViewActionHandlers()

        handlers.dispatchInTest(WebViewAction.CLICK)

        assertEquals(BridgeMessage.SUCCESS_PAYLOAD, responded)
        assertNull(refused)
    }

    @Test
    fun `a question the surface cannot answer is refused, not left silent`() {
        // Silence costs the page its own timeout and tells it nothing; an empty answer it would
        // take for the truth.
        val handlers = WebViewActionHandlers()

        handlers.dispatchInTest(WebViewAction.FILTER_SHOWABLE_INAPPS)

        assertNull(responded)
        assertTrue(refused is IllegalArgumentException)
        assertTrue(refused!!.message!!.contains(WebViewAction.FILTER_SHOWABLE_INAPPS.name))
    }

    @Test
    fun `an action that needs the user is refused while nobody is looking`() {
        val handlers = WebViewActionHandlers()
        var ran = false
        handlers.register(WebViewAction.OPEN_LINK) {
            ran = true
            BridgeMessage.SUCCESS_PAYLOAD
        }

        handlers.dispatchInTest(WebViewAction.OPEN_LINK, isUserPresent = false)

        // Read before the handler, not inside it: the gate is the same wherever the action is served.
        assertEquals(false, ran)
        assertTrue(refused is IllegalStateException)
        assertEquals(NOBODY_LOOKING_ERROR, refused?.message)
    }

    @Test
    fun `an action that does not need the user runs while nobody is looking`() {
        val handlers = WebViewActionHandlers()
        handlers.register(WebViewAction.LOG) { "logged" }

        handlers.dispatchInTest(WebViewAction.LOG, isUserPresent = false)

        assertEquals("logged", responded)
    }

    @Test
    fun `a handler that throws is refused with its own error`() {
        val handlers = WebViewActionHandlers()
        handlers.register(WebViewAction.SYNC_OPERATION) { throw IllegalStateException("boom") }

        handlers.dispatchInTest(WebViewAction.SYNC_OPERATION)

        assertNull(responded)
        assertEquals("boom", refused?.message)
    }

    @Test
    fun `a surface released while a suspending handler was in flight answers nothing`() {
        // The page is gone and so is whoever would have received the answer; the holder must not
        // speak for it.
        val handlers = WebViewActionHandlers()
        var alive = true
        handlers.registerSuspend(WebViewAction.READY) { "ready-payload" }

        handlers.dispatchInTest(WebViewAction.READY, isAlive = { alive.also { alive = false } })
        responded = null
        handlers.dispatchInTest(WebViewAction.READY, isAlive = { false })

        assertNull(responded)
        assertNull(refused)
    }

    private fun createRequest(action: WebViewAction): BridgeMessage.Request = BridgeMessage.Request(
        version = BridgeMessage.VERSION,
        action = action,
        payload = BridgeMessage.EMPTY_PAYLOAD,
        id = "request-id",
        timestamp = 1L,
    )
}
