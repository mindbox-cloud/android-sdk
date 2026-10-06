package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.app.Activity
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.di.modules.AppModule
import cloud.mindbox.mobile_sdk.di.modules.DataModule
import cloud.mindbox.mobile_sdk.inapp.data.managers.SEND_INAPP_TAGS_FEATURE
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.FeatureToggleManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManager
import cloud.mindbox.mobile_sdk.inapp.presentation.OnShowInAppOutcome
import cloud.mindbox.mobile_sdk.inapp.presentation.ShowInAppOutcome
import cloud.mindbox.mobile_sdk.models.InAppStub
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
    private val permissionManager: PermissionManager = mockk(relaxed = true)
    private val inAppMessageManager: InAppMessageManager = mockk()
    private val mobileConfigRepository: MobileConfigRepository = mockk {
        every { findInAppInCurrentConfig(any()) } returns null
    }
    private val featureToggleManager: FeatureToggleManager = mockk {
        every { isEnabled(SEND_INAPP_TAGS_FEATURE) } returns true
    }
    private val operationSender: WebViewOperationSender = mockk(relaxUnitFun = true)

    private class FakeHost(
        override val closeCapability: ((BridgeMessage.Request) -> String)? = null,
        override val hideCapability: (() -> String)? = null,
        override val isRequesterActive: Boolean = true,
        override val hostActivity: Activity? = null,
    ) : WebViewBridgeHost {

        override val hostTags: Map<String, String> = mapOf("templateType" to "Embedded")
        override val hostPage: MindboxWebPage = MindboxWebPage { _, _ -> }
        override val hostInAppId: String = "host-id"

        val sentToPage = mutableListOf<BridgeMessage.Request>()

        override fun sendToPage(message: BridgeMessage.Request, onError: (String?) -> Unit) {
            sentToPage.add(message)
        }

        override fun requireCanShowInApp() = Unit
    }

    @Before
    fun setUp() {
        MindboxDI.appModule = mockk<AppModule>(relaxed = true) {
            every { appContext } returns application
            every { gson } returns DataModule(mockk(relaxed = true), mockk(relaxed = true)).gson
            every { webPageRegistry } returns this@WebViewCommonBridgeActionsTest.webPageRegistry
            every { permissionManager } returns this@WebViewCommonBridgeActionsTest.permissionManager
            every { inAppMessageManager } returns this@WebViewCommonBridgeActionsTest.inAppMessageManager
            every { mobileConfigRepository } returns this@WebViewCommonBridgeActionsTest.mobileConfigRepository
            every { featureToggleManager } returns this@WebViewCommonBridgeActionsTest.featureToggleManager
        }
    }

    private fun handlersOf(host: WebViewBridgeHost): WebViewActionHandlers =
        WebViewActionHandlers().also { handlers -> WebViewCommonBridgeActions(host, operationSender).register(handlers) }

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

    private fun WebViewActionHandlers.refusalCode(action: WebViewAction, payload: String): BridgeErrorCode =
        assertThrows("$action with $payload", BridgeRefusalException::class.java) {
            runBlocking {
                suspendHandler(action)?.invoke(request(action, payload)) ?: answer(action, payload)
            }
        }.code

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
    fun `a permission type that is not a non-empty string is refused as invalid_payload`() {
        val handlers = handlersOf(FakeHost())

        listOf("""{}""", """{"type":""}""", """{"type":5}""", """{"type":null}""", "abc", "").forEach { payload ->
            assertEquals(payload, BridgeErrorCode.INVALID_PAYLOAD, handlers.refusalCode(WebViewAction.PERMISSION_REQUEST, payload))
        }
    }

    @Test
    fun `a permission type the SDK does not know is refused as unsupported_value`() {
        val handlers = handlersOf(FakeHost())

        listOf("""{"type":"camera"}""", """{"type":" "}""").forEach { payload ->
            assertEquals(payload, BridgeErrorCode.UNSUPPORTED_VALUE, handlers.refusalCode(WebViewAction.PERMISSION_REQUEST, payload))
        }
    }

    @Test
    fun `a permission request the system could not start is refused as permission_failed`() {
        every { permissionManager.getNotificationPermissionStatus() } throws IllegalStateException("no activity to start")
        val payload = """{"type":"pushNotifications"}"""

        assertEquals(
            BridgeErrorCode.PERMISSION_FAILED,
            handlersOf(FakeHost()).refusalCode(WebViewAction.PERMISSION_REQUEST, payload),
        )
        assertEquals(
            BridgeErrorCode.PERMISSION_FAILED,
            handlersOf(FakeHost(hostActivity = mockk(relaxed = true))).refusalCode(WebViewAction.PERMISSION_REQUEST, payload),
        )
    }

    @Test
    fun `a settings target that is not a non-empty string is refused as invalid_payload`() {
        val handlers = handlersOf(FakeHost())

        listOf("""{}""", """{"target":""}""", """{"target":5}""", """{"target":true}""", """{"target":{}}""", "abc", "[]")
            .forEach { payload ->
                assertEquals(payload, BridgeErrorCode.INVALID_PAYLOAD, handlers.refusalCode(WebViewAction.SETTINGS_OPEN, payload))
            }
    }

    @Test
    fun `a settings target the SDK does not know is refused as unsupported_value`() {
        val handlers = handlersOf(FakeHost())

        listOf("""{"target":"bluetooth"}""", """{"target":" "}""").forEach { payload ->
            assertEquals(payload, BridgeErrorCode.UNSUPPORTED_VALUE, handlers.refusalCode(WebViewAction.SETTINGS_OPEN, payload))
        }
    }

    @Test
    fun `settings with a known target and no activity to open them from is refused as open_failed`() {
        assertEquals(
            BridgeErrorCode.OPEN_FAILED,
            handlersOf(FakeHost()).refusalCode(WebViewAction.SETTINGS_OPEN, """{"target":"notifications"}"""),
        )
    }

    @Test
    fun `motion gestures with any element that is not a non-empty string are refused as invalid_payload, in any order`() {
        val handlers = handlersOf(FakeHost())

        listOf(
            """{}""",
            """{"gestures":[]}""",
            """{"gestures":"shake"}""",
            "abc",
            """{"gestures":[""]}""",
            """{"gestures":[null]}""",
            """{"gestures":[1,"wave"]}""",
            """{"gestures":["wave",1]}""",
        ).forEach { payload ->
            assertEquals(payload, BridgeErrorCode.INVALID_PAYLOAD, handlers.refusalCode(WebViewAction.MOTION_START, payload))
        }
    }

    @Test
    fun `motion gestures that are all strings but include an unknown name are refused as unsupported_value`() {
        val handlers = handlersOf(FakeHost())

        listOf("""{"gestures":["wave"]}""", """{"gestures":[" "]}""", """{"gestures":["flip","wave"]}""").forEach { payload ->
            assertEquals(payload, BridgeErrorCode.UNSUPPORTED_VALUE, handlers.refusalCode(WebViewAction.MOTION_START, payload))
        }
    }

    @Test
    fun `an operation naming another in-app carries that in-app's tags, one naming the page's own in-app the host's`() {
        every { mobileConfigRepository.findInAppInCurrentConfig("story-2") } returns
            InAppStub.getInApp().copy(id = "story-2", tags = mapOf("story" to "second"))
        val handlers = handlersOf(FakeHost())

        handlers.answer(WebViewAction.ASYNC_OPERATION, """{"operation":"OpenScreen","inappId":"story-2","body":{}}""")
        handlers.answer(WebViewAction.ASYNC_OPERATION, """{"operation":"OpenScreen","inappId":"host-id","body":{}}""")

        verifyOrder {
            operationSender.asyncOperation(application, "OpenScreen", """{"tags":{"story":"second"}}""")
            operationSender.asyncOperation(application, "OpenScreen", """{"tags":{"templateType":"Embedded"}}""")
        }
        verify(exactly = 0) { mobileConfigRepository.findInAppInCurrentConfig("host-id") }
    }

    @Test
    fun `an operation whose inappId is not a non-empty string is refused as invalid_payload and nothing is sent`() {
        every { operationSender.syncOperation(any(), any(), any(), any()) } answers { arg<(String) -> Unit>(2)("{}") }
        val handlers = handlersOf(FakeHost())

        listOf(WebViewAction.ASYNC_OPERATION, WebViewAction.SYNC_OPERATION).forEach { action ->
            listOf("""{"operation":"OpenScreen","inappId":5,"body":{}}""", """{"operation":"OpenScreen","inappId":"","body":{}}""")
                .forEach { payload ->
                    assertEquals("$action with $payload", BridgeErrorCode.INVALID_PAYLOAD, handlers.refusalCode(action, payload))
                }
        }
        verify(exactly = 0) { operationSender.asyncOperation(any(), any(), any()) }
        verify(exactly = 0) { operationSender.syncOperation(any(), any(), any(), any()) }
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

    @Test
    fun `a show request still waiting when the page is torn down ends without an answer`() = runBlocking {
        val outcome = slot<OnShowInAppOutcome>()
        every { inAppMessageManager.showInAppById(any(), any(), any(), capture(outcome)) } just runs
        val actions = WebViewCommonBridgeActions(FakeHost())
        val handler = WebViewActionHandlers().also(actions::register).suspendHandler(WebViewAction.SHOW_IN_APP)!!
        val answer = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { handler(request(WebViewAction.SHOW_IN_APP, """{"inappId":"story-2"}""")) }
        }

        actions.tearDown()
        outcome.captured.onOutcome(ShowInAppOutcome.Shown)

        assertTrue(answer.await().exceptionOrNull() is CancellationException)
    }

    @Test
    fun `a show request reaching a torn-down page never asks for the show`() {
        every { inAppMessageManager.showInAppById(any(), any(), any(), any()) } answers {
            arg<OnShowInAppOutcome>(3).onOutcome(ShowInAppOutcome.Shown)
        }
        val actions = WebViewCommonBridgeActions(FakeHost())
        val handler = WebViewActionHandlers().also(actions::register).suspendHandler(WebViewAction.SHOW_IN_APP)!!

        actions.tearDown()
        val answer = runBlocking { runCatching { handler(request(WebViewAction.SHOW_IN_APP, """{"inappId":"story-2"}""")) } }

        assertTrue(answer.exceptionOrNull() is CancellationException)
        verify(exactly = 0) { inAppMessageManager.showInAppById(any(), any(), any(), any()) }
    }
}
