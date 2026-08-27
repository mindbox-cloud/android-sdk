package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.app.Activity
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.di.modules.AppModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The half of the shared seam an overlay takes and a block does not: the capabilities only a
 * surface with a window has. Every block test walks the other branch of both, so a regression
 * that leaves an overlay without its `close` would keep them all green — this class is where the
 * two host shapes are pinned side by side.
 *
 * The presence gate is not here on purpose: it moved to the dispatcher, where one rule serves
 * every surface (see `WebViewActionHandlersTest`).
 */
@RunWith(RobolectricTestRunner::class)
class WebViewCommonBridgeActionsTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val webPageRegistry: MindboxWebPageRegistry = mockk(relaxUnitFun = true)

    private class FakeHost(
        override val closeCapability: ((BridgeMessage.Request) -> String)? = null,
        override val hideCapability: (() -> String)? = null,
        override val isUserPresent: Boolean = true,
    ) : WebViewBridgeHost {

        override val hostActivity: Activity? = null
        override val hostTags: Map<String, String> = mapOf("templateType" to "Embedded")
        override val hostPage: MindboxWebPage = MindboxWebPage { _, _ -> }

        val sentToPage = mutableListOf<BridgeMessage.Request>()

        override fun sendToPage(message: BridgeMessage.Request, onError: (String?) -> Unit) {
            sentToPage.add(message)
        }
    }

    @Before
    fun setUp() {
        MindboxDI.appModule = mockk<AppModule>(relaxed = true) {
            every { appContext } returns application
            every { webPageRegistry } returns this@WebViewCommonBridgeActionsTest.webPageRegistry
        }
    }

    private fun handlersOf(host: WebViewBridgeHost): WebViewActionHandlers =
        WebViewActionHandlers().also { handlers -> WebViewCommonBridgeActions(host).register(handlers) }

    private fun WebViewActionHandlers.serves(action: WebViewAction): Boolean =
        handler(action) != null || suspendHandler(action) != null

    private fun request(action: WebViewAction, payload: String = BridgeMessage.EMPTY_PAYLOAD) =
        BridgeMessage.Request(
            version = BridgeMessage.VERSION,
            action = action,
            payload = payload,
            id = "request-id",
            timestamp = 1L,
        )

    private fun WebViewActionHandlers.answer(action: WebViewAction, payload: String = BridgeMessage.EMPTY_PAYLOAD): String? {
        val handler = handler(action) ?: return null
        return handler(request(action, payload))
    }

    @Test
    fun `close reaches the window of a surface that has one`() {
        var closedWith: BridgeMessage.Request? = null
        val host = FakeHost(
            closeCapability = { message ->
                closedWith = message
                """{"closed":true}"""
            },
        )

        val answer = handlersOf(host).answer(WebViewAction.CLOSE)

        assertEquals("""{"closed":true}""", answer)
        assertEquals(WebViewAction.CLOSE, closedWith?.action)
    }

    @Test
    fun `close on a surface with no window is acknowledged, not silently dropped`() {
        val host = FakeHost(closeCapability = null)

        val answer = handlersOf(host).answer(WebViewAction.CLOSE)

        assertEquals(BridgeMessage.SUCCESS_PAYLOAD, answer)
    }

    @Test
    fun `hide reaches the window of a surface that has one`() {
        var hidden = false
        val host = FakeHost(
            hideCapability = {
                hidden = true
                """{"hidden":true}"""
            },
        )

        val answer = handlersOf(host).answer(WebViewAction.HIDE)

        assertEquals("""{"hidden":true}""", answer)
        assertTrue(hidden)
    }

    @Test
    fun `hide on a surface with no window is acknowledged`() {
        val host = FakeHost(hideCapability = null)

        val answer = handlersOf(host).answer(WebViewAction.HIDE)

        assertEquals(BridgeMessage.SUCCESS_PAYLOAD, answer)
    }

    @Test
    fun `an action that changes nothing still answers the contract success`() {
        // `{}` is not what the protocol calls a successful answer, and the page is free to tell
        // the two apart on any action it decides to wait on.
        val handlers = handlersOf(FakeHost())

        assertEquals(BridgeMessage.SUCCESS_PAYLOAD, handlers.answer(WebViewAction.LOG, """{"message":"hi"}"""))
        assertEquals(BridgeMessage.SUCCESS_PAYLOAD, handlers.answer(WebViewAction.MOTION_STOP))
    }

    @Test
    fun `both host shapes serve the same vocabulary`() {
        // The seam is what makes an overlay and a block speak one protocol: whichever half of a
        // capability a surface has, the set of actions it answers is the same.
        val withWindow = handlersOf(FakeHost(closeCapability = { "{}" }, hideCapability = { "{}" }))
        val withoutWindow = handlersOf(FakeHost())

        WebViewAction.entries.forEach { action ->
            assertEquals(
                "action $action is served differently by the two host shapes",
                withWindow.serves(action),
                withoutWindow.serves(action),
            )
        }
        assertEquals(false, withWindow.serves(WebViewAction.READY))
    }

    @Test
    fun `a local state write is broadcast to every other live page`() {
        val host = FakeHost()
        val handlers = handlersOf(host)
        val handler = handlers.suspendHandler(WebViewAction.LOCAL_STATE_SET)!!

        val answer = runBlocking {
            handler(request(WebViewAction.LOCAL_STATE_SET, """{"data":{"inapp.completed.1":"true"}}"""))
        }

        // The author already holds the answer; everyone else learns without being asked — this is
        // how a page dims an element while the in-app that wrote it is still on top.
        verify {
            webPageRegistry.broadcast(WebViewAction.LOCAL_STATE_CHANGED, answer, excludingAuthor = host.hostPage)
        }
    }
}
