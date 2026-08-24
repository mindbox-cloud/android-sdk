package cloud.mindbox.mobile_sdk.embedded.webview

import android.app.Application
import android.os.Looper
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.di.modules.AppModule
import cloud.mindbox.mobile_sdk.di.modules.DataModule
import cloud.mindbox.mobile_sdk.embedded.EmbeddedBlockState
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionStatus
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFailureTracker
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppWebViewCachePolicy
import cloud.mindbox.mobile_sdk.inapp.presentation.view.MindboxWebPageRegistry
import cloud.mindbox.mobile_sdk.inapp.presentation.view.WebViewAction
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason
import cloud.mindbox.mobile_sdk.utils.SystemTimeProvider
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The feed holder against the real stories-page protocol, driven through the real bridge
 * transport (`SdkBridge` js interface): `ready` answers with `stories` as a JSON array,
 * `filterShowableInapps` returns the interactor's subset, `contentRendered {count}` is the
 * readiness signal, and `initDataUpdated` refreshes the feed without recreating the webview.
 */
@RunWith(RobolectricTestRunner::class)
class EmbeddedBlockWebViewHolderTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val realGson = DataModule(mockk(relaxed = true), mockk(relaxed = true)).gson

    private val gatewayManager: GatewayManager = mockk()
    private val inAppInteractor: InAppInteractor = mockk()
    private val inAppMessageManager: InAppMessageManager = mockk(relaxUnitFun = true)
    private val webPageRegistry: MindboxWebPageRegistry = mockk(relaxUnitFun = true)
    private val inAppFailureTracker: InAppFailureTracker = mockk(relaxed = true)

    /** What [SystemTimeProvider] would return: the tests move it by hand. */
    private var elapsed = 0L
    private val timeProvider: SystemTimeProvider = mockk {
        every { elapsedSince(any()) } answers { Milliseconds(elapsed) }
    }
    private val permissionManager: PermissionManager = mockk {
        every { getCameraPermissionStatus() } returns PermissionStatus.DENIED
        every { getLocationPermissionStatus() } returns PermissionStatus.DENIED
        every { getMicrophonePermissionStatus() } returns PermissionStatus.DENIED
        every { getNotificationPermissionStatus() } returns PermissionStatus.DENIED
        every { getPhotoLibraryPermissionStatus() } returns PermissionStatus.DENIED
    }

    private val states = mutableListOf<EmbeddedBlockState>()

    private lateinit var holder: EmbeddedBlockWebViewHolder

    @Before
    fun setUp() {
        MindboxDI.appModule = mockk<AppModule>(relaxed = true) {
            every { gson } returns realGson
            every { gatewayManager } returns this@EmbeddedBlockWebViewHolderTest.gatewayManager
            every { inAppInteractor } returns this@EmbeddedBlockWebViewHolderTest.inAppInteractor
            every { permissionManager } returns this@EmbeddedBlockWebViewHolderTest.permissionManager
            every { appContext } returns application
            every { inAppFailureTracker } returns this@EmbeddedBlockWebViewHolderTest.inAppFailureTracker
            every { timeProvider } returns this@EmbeddedBlockWebViewHolderTest.timeProvider
            every { webViewCachePolicy } returns mockk<InAppWebViewCachePolicy> {
                every { isCacheEnabled } returns false
            }
            every { inAppMessageManager } returns this@EmbeddedBlockWebViewHolderTest.inAppMessageManager
            every { webPageRegistry } returns this@EmbeddedBlockWebViewHolderTest.webPageRegistry
        }
        mockkObject(DbManager)
        every { DbManager.listenConfigurations() } returns flowOf(
            mockk<Configuration>(relaxed = true) {
                every { endpointId } returns "endpoint-id"
                every { versionName } returns "1.0"
            }
        )
        coEvery { gatewayManager.fetchWebViewContent(any()) } returns "<html>feed</html>"

        holder = EmbeddedBlockWebViewHolder(
            inAppId = "embedded-id",
            layer = InAppStub.getEmbeddedWebViewLayer(),
            context = application,
            attemptStartedAt = Timestamp(0L),
        )
        holder.onStateChange = { state -> states.add(state) }
    }

    @After
    fun tearDown() {
        holder.release()
        unmockkObject(DbManager)
    }

    private val webView: WebView
        get() = holder.run {
            // The content view is gated by readiness; reach the raw view through the bridge
            // registration instead.
            webViewField()
        }

    private fun EmbeddedBlockWebViewHolder.webViewField(): WebView {
        val field = EmbeddedBlockWebViewHolder::class.java.getDeclaredField("webViewController")
        field.isAccessible = true
        val controller = field.get(this) as cloud.mindbox.mobile_sdk.inapp.webview.WebViewController
        return controller.view as WebView
    }

    private fun await(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Condition not met within ${timeoutMs}ms")
            }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private fun startAndAwaitPageLoad() {
        holder.start()
        await { shadowOf(webView).lastLoadDataWithBaseURL != null }
    }

    private fun bridge(): Any = shadowOf(webView).getJavascriptInterface("SdkBridge")

    private fun postFromPage(json: String) {
        val bridge = bridge()
        val postMessage = bridge.javaClass.getDeclaredMethod("postMessage", String::class.java)
        postMessage.isAccessible = true
        postMessage.invoke(bridge, json)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun request(action: String, payload: String, id: String = "req-1"): String =
        """{"type":"request","action":"$action","payload":${com.google.gson.Gson().toJson(payload)},"id":"$id","version":1,"timestamp":1}"""

    /** Unwraps the JS bridge call back into the outgoing message payload. */
    private fun lastOutgoingMessage(): JsonObject? {
        val script = shadowOf(webView).lastEvaluatedJavascript ?: return null
        val quoted = Regex("""emit\((".*")\);return""").find(script)?.groupValues?.get(1) ?: return null
        val json = JSONTokener(quoted).nextValue() as String
        return JsonParser.parseString(json).asJsonObject
    }

    private fun lastOutgoingPayload(): JsonObject? =
        lastOutgoingMessage()?.get("payload")?.asString?.let { JsonParser.parseString(it).asJsonObject }

    @Test
    fun `content page is fetched through the gateway and loaded with the base url`() {
        startAndAwaitPageLoad()

        val loaded = shadowOf(webView).lastLoadDataWithBaseURL
        assertEquals("<html>feed</html>", loaded?.data)
        assertEquals("https://feed.local/base", loaded?.baseUrl)
    }

    @Test
    fun `ready response contains stories as json array not string`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "ready", payload = "{}"))
        await { lastOutgoingMessage()?.get("action")?.asString == "ready" }

        val payload = lastOutgoingPayload()!!
        assertTrue(payload.get("stories").isJsonArray)
        assertEquals(
            "story-1",
            payload.getAsJsonArray("stories").get(0).asJsonObject.get("inAppId").asString
        )
        assertEquals("endpoint-id", payload.get("endpointId").asString)
    }

    @Test
    fun `filterShowableInapps returns subset from the interactor`() {
        coEvery { inAppInteractor.filterShowableInAppIds(listOf("story-1", "story-2")) } returns
            listOf("story-1")
        startAndAwaitPageLoad()

        postFromPage(request(action = "filterShowableInapps", payload = """{"inappIds":["story-1","story-2"]}"""))
        await { lastOutgoingMessage()?.get("action")?.asString == "filterShowableInapps" }

        val payload = lastOutgoingPayload()!!
        assertEquals(1, payload.getAsJsonArray("inappIds").size())
        assertEquals("story-1", payload.getAsJsonArray("inappIds").get(0).asString)
    }

    @Test
    fun `filterShowableInapps without an inappIds array is refused with an error`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "filterShowableInapps", payload = "{}"))
        await { lastOutgoingMessage()?.get("action")?.asString == "filterShowableInapps" }

        // A refusal the page can retry, not an empty answer it would take for the truth.
        assertEquals("error", lastOutgoingMessage()!!.get("type").asString)
        coVerify(exactly = 0) { inAppInteractor.filterShowableInAppIds(any()) }
    }

    @Test
    fun `filterShowableInapps skips non-string ids and answers the rest`() {
        coEvery { inAppInteractor.filterShowableInAppIds(listOf("story-1")) } returns listOf("story-1")
        startAndAwaitPageLoad()

        postFromPage(request(action = "filterShowableInapps", payload = """{"inappIds":["story-1",7]}"""))
        await { lastOutgoingMessage()?.get("action")?.asString == "filterShowableInapps" }

        assertEquals("response", lastOutgoingMessage()!!.get("type").asString)
        assertEquals("story-1", lastOutgoingPayload()!!.getAsJsonArray("inappIds").get(0).asString)
    }

    @Test
    fun `contentRendered with positive count switches state to Ready and counts the show`() {
        coEvery { inAppInteractor.recordBlockShow(any(), any(), any()) } just runs
        startAndAwaitPageLoad()

        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))

        await { states.lastOrNull() == EmbeddedBlockState.Ready }
        assertTrue(holder.contentView != null)
        // Content on screen is a show, counted like any other in-app's; the frequency decides
        // inside the interactor whether there is anything to write.
        coVerify(timeout = 5_000L) { inAppInteractor.recordBlockShow("embedded-id", any(), any()) }
    }

    @Test
    fun `contentRendered with zero count switches state to Empty and reports nothing`() {
        coEvery { inAppInteractor.recordBlockShow(any(), any(), any()) } just runs
        startAndAwaitPageLoad()

        postFromPage(request(action = "contentRendered", payload = """{"count":0}"""))

        await { states.lastOrNull() == EmbeddedBlockState.Empty }
        coVerify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any()) }
    }

    @Test
    fun `contentRendered without a readable count fails the block and refuses the page`() {
        coEvery { inAppInteractor.recordBlockShow(any(), any(), any()) } just runs
        startAndAwaitPageLoad()

        postFromPage(request(action = "contentRendered", payload = """{"count":"many"}"""))

        await { states.lastOrNull() == EmbeddedBlockState.Failed }
        coVerify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any()) }
        // The page must hear the refusal too: a success response would pass for the truth.
        assertEquals("error", lastOutgoingMessage()!!.get("type").asString)
    }

    @Test
    fun `contentRendered with a negative count fails the block instead of passing for empty`() {
        coEvery { inAppInteractor.recordBlockShow(any(), any(), any()) } just runs
        startAndAwaitPageLoad()

        postFromPage(request(action = "contentRendered", payload = """{"count":-1}"""))

        await { states.lastOrNull() == EmbeddedBlockState.Failed }
        coVerify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any()) }
        assertEquals("error", lastOutgoingMessage()!!.get("type").asString)
    }

    @Test
    fun `contentRendered with a fractional count is refused rather than rounded`() {
        coEvery { inAppInteractor.recordBlockShow(any(), any(), any()) } just runs
        startAndAwaitPageLoad()

        postFromPage(request(action = "contentRendered", payload = """{"count":2.5}"""))

        await { states.lastOrNull() == EmbeddedBlockState.Failed }
        coVerify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any()) }
        assertEquals("error", lastOutgoingMessage()!!.get("type").asString)
    }

    @Test
    fun `contentRendered with a whole double count is a valid report`() {
        coEvery { inAppInteractor.recordBlockShow(any(), any(), any()) } just runs
        startAndAwaitPageLoad()

        postFromPage(request(action = "contentRendered", payload = """{"count":3.0}"""))

        await { states.lastOrNull() == EmbeddedBlockState.Ready }
        coVerify(timeout = 5_000L) { inAppInteractor.recordBlockShow("embedded-id", any(), any()) }
    }

    @Test
    fun `the show is reported once per content instance`() {
        coEvery { inAppInteractor.recordBlockShow(any(), any(), any()) } just runs
        startAndAwaitPageLoad()

        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))
        await { states.lastOrNull() == EmbeddedBlockState.Ready }
        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))

        coVerify(exactly = 1, timeout = 5_000L) { inAppInteractor.recordBlockShow("embedded-id", any(), any()) }
    }

    @Test
    fun `showInApp from the page is acknowledged and reports nothing`() {
        // The ack says the request was handed over, never that a window opened: the block's own
        // show accounting must not be spent on a tap.
        coEvery { inAppInteractor.recordBlockShow(any(), any(), any()) } just runs
        startAndAwaitPageLoad()

        postFromPage(
            request(action = "showInApp", payload = """{"inappId":"story-1","index":0,"params":{}}""")
        )
        await { lastOutgoingMessage()?.get("action")?.asString == "showInApp" }

        assertEquals("response", lastOutgoingMessage()?.get("type")?.asString)
        assertTrue(lastOutgoingPayload()!!.get("success").asBoolean)
        coVerify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any()) }
    }

    @Test
    fun `showInApp hands the id and the params to the show path`() {
        startAndAwaitPageLoad()

        postFromPage(
            request(
                action = "showInApp",
                payload = """{"inappId":"story-1","index":0,"sourceInappId":"feed-id","params":{"title":"Сториз 1","record":{"rank":3}}}"""
            )
        )
        await { lastOutgoingMessage()?.get("action")?.asString == "showInApp" }

        verify(exactly = 1) {
            inAppMessageManager.showInAppById(
                "story-1",
                mapOf(
                    "title" to JsonPrimitive("Сториз 1"),
                    "record" to JsonParser.parseString("""{"rank":3}"""),
                )
            )
        }
    }

    @Test
    fun `showInApp sent with an object payload works the same as a string one`() {
        startAndAwaitPageLoad()

        // The whole envelope is one JSON document: payload is a plain object, not a quoted string.
        postFromPage(
            """{"type":"request","action":"showInApp","payload":{"inappId":"story-1","params":{"a":1}},"id":"req-obj","version":1,"timestamp":1}"""
        )
        await { lastOutgoingMessage()?.get("action")?.asString == "showInApp" }

        assertEquals("response", lastOutgoingMessage()?.get("type")?.asString)
        verify(exactly = 1) {
            inAppMessageManager.showInAppById("story-1", mapOf("a" to JsonPrimitive(1)))
        }
    }

    @Test
    fun `showInApp without an inappId is refused with an error`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "showInApp", payload = """{"params":{}}"""))
        await { lastOutgoingMessage()?.get("action")?.asString == "showInApp" }

        assertEquals("error", lastOutgoingMessage()?.get("type")?.asString)
        verify(exactly = 0) { inAppMessageManager.showInAppById(any(), any()) }
    }

    @Test
    fun `showInApp from a paused block is refused - nobody is looking at the page`() {
        startAndAwaitPageLoad()
        holder.pause()

        postFromPage(request(action = "showInApp", payload = """{"inappId":"story-1"}"""))
        await { lastOutgoingMessage()?.get("action")?.asString == "showInApp" }

        assertEquals("error", lastOutgoingMessage()?.get("type")?.asString)
        verify(exactly = 0) { inAppMessageManager.showInAppById(any(), any()) }
    }

    @Test
    fun `showInApp from a failed attempt is acked and ignored`() {
        startAndAwaitPageLoad()
        postFromPage(request(action = "contentRendered", payload = """{"count":-1}""", id = "bad"))
        await { states.lastOrNull() == EmbeddedBlockState.Failed }

        postFromPage(request(action = "showInApp", payload = """{"inappId":"story-1"}"""))
        await { lastOutgoingMessage()?.get("action")?.asString == "showInApp" }

        assertEquals("response", lastOutgoingMessage()?.get("type")?.asString)
        verify(exactly = 0) { inAppMessageManager.showInAppById(any(), any()) }
    }

    @Test
    fun `the old checkInappsTargeting name is not spoken anymore`() {
        // Cut hard, in sync with iOS: the action never shipped, so no installed SDK speaks it.
        startAndAwaitPageLoad()

        postFromPage(request(action = "checkInappsTargeting", payload = """{"inappIds":["story-1"]}"""))

        coVerify(exactly = 0) { inAppInteractor.filterShowableInAppIds(any()) }
        assertTrue(lastOutgoingMessage()?.get("action")?.asString != "checkInappsTargeting")
    }

    @Test
    fun `the page joins the broadcast set on its first ready only`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "ready", payload = "{}", id = "ready-1"))
        await { lastOutgoingMessage()?.get("action")?.asString == "ready" }
        postFromPage(request(action = "ready", payload = "{}", id = "ready-2"))

        verify(exactly = 1) { webPageRegistry.register(holder) }
    }

    @Test
    fun `release unregisters the page from broadcasts`() {
        startAndAwaitPageLoad()
        postFromPage(request(action = "ready", payload = "{}", id = "ready-1"))
        await { lastOutgoingMessage()?.get("action")?.asString == "ready" }

        holder.release()

        verify(exactly = 1) { webPageRegistry.unregister(holder) }
    }

    @Test
    fun `release before the first ready has nothing to unregister`() {
        startAndAwaitPageLoad()

        holder.release()

        verify(exactly = 0) { webPageRegistry.unregister(any()) }
    }

    @Test
    fun `local state set is broadcast to every other page`() {
        startAndAwaitPageLoad()

        postFromPage(
            request(action = "localState.set", payload = """{"data":{"inapp.completed.story-1":"rev-1"}}""")
        )
        await { lastOutgoingMessage()?.get("action")?.asString == "localState.set" }

        // The author gets the ordinary answer; everyone else hears the change with the same payload.
        val answer = lastOutgoingMessage()!!.get("payload").asString
        verify(exactly = 1) {
            webPageRegistry.broadcast(WebViewAction.LOCAL_STATE_CHANGED, answer, holder)
        }
    }

    @Test
    fun `a pushed broadcast reaches the page as a request`() {
        startAndAwaitPageLoad()

        holder.push(WebViewAction.LOCAL_STATE_CHANGED, """{"data":{},"version":1}""")
        await { lastOutgoingMessage()?.get("action")?.asString == "localState.changed" }

        assertEquals("request", lastOutgoingMessage()?.get("type")?.asString)
    }

    @Test
    fun `a released page drops the push`() {
        startAndAwaitPageLoad()
        val web = webView
        holder.release()
        val before = shadowOf(web).lastEvaluatedJavascript

        holder.push(WebViewAction.LOCAL_STATE_CHANGED, "{}")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(before, shadowOf(web).lastEvaluatedJavascript)
    }

    @Test
    fun `close and hide at the block are acked and ignored - there is no window`() {
        startAndAwaitPageLoad()
        val statesBefore = states.toList()

        postFromPage(request(action = "close", payload = "{}", id = "close-1"))
        await { lastOutgoingMessage()?.get("action")?.asString == "close" }
        assertEquals("response", lastOutgoingMessage()?.get("type")?.asString)
        assertTrue(lastOutgoingPayload()!!.get("success").asBoolean)

        postFromPage(request(action = "hide", payload = "{}", id = "hide-1"))
        await { lastOutgoingMessage()?.get("action")?.asString == "hide" }
        assertEquals("response", lastOutgoingMessage()?.get("type")?.asString)

        assertEquals(statesBefore, states.toList())
    }

    @Test
    fun `actions taken on the user's behalf are refused when nobody is looking`() {
        startAndAwaitPageLoad()
        holder.pause()

        listOf("openLink", "settings.open", "permission.request", "haptic", "motion.start")
            .forEachIndexed { index, action ->
                postFromPage(request(action = action, payload = "{}", id = "gated-$index"))
                await { lastOutgoingMessage()?.get("action")?.asString == action }
                assertEquals("error for $action", "error", lastOutgoingMessage()?.get("type")?.asString)
            }
    }

    @Test
    fun `openLink with an unusable payload is refused`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "openLink", payload = "{}"))
        await { lastOutgoingMessage()?.get("action")?.asString == "openLink" }

        assertEquals("error", lastOutgoingMessage()?.get("type")?.asString)
    }

    @Test
    fun `haptic with an unusable payload is swallowed with an empty answer`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "haptic", payload = "{}"))
        await { lastOutgoingMessage()?.get("action")?.asString == "haptic" }

        assertEquals("response", lastOutgoingMessage()?.get("type")?.asString)
    }

    @Test
    fun `motion start without a usable gesture is refused`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "motion.start", payload = """{"gestures":[]}"""))
        await { lastOutgoingMessage()?.get("action")?.asString == "motion.start" }

        assertEquals("error", lastOutgoingMessage()?.get("type")?.asString)
    }

    @Test
    fun `log from the page is acknowledged`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "log", payload = """"page says hi""""))
        await { lastOutgoingMessage()?.get("action")?.asString == "log" }

        assertEquals("response", lastOutgoingMessage()?.get("type")?.asString)
    }

    @Test
    fun `motion start with a known gesture but no sensors is refused`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "motion.start", payload = """{"gestures":["shake"]}"""))
        await { lastOutgoingMessage()?.get("action")?.asString == "motion.start" }

        // Robolectric offers no accelerometer: the page hears which gesture has no sensor.
        assertEquals("error", lastOutgoingMessage()?.get("type")?.asString)
    }

    @Test
    fun `motion stop is always acknowledged`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "motion.stop", payload = "{}"))
        await { lastOutgoingMessage()?.get("action")?.asString == "motion.stop" }

        assertEquals("response", lastOutgoingMessage()?.get("type")?.asString)
        assertTrue(lastOutgoingPayload()!!.get("success").asBoolean)
    }

    @Test
    fun `settings open with an unusable payload is refused`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "settings.open", payload = "{}"))
        await { lastOutgoingMessage()?.get("action")?.asString == "settings.open" }

        assertEquals("error", lastOutgoingMessage()?.get("type")?.asString)
    }

    @Test
    fun `permission request with an unknown type is refused`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "permission.request", payload = """{"type":"unknown"}"""))
        await { lastOutgoingMessage()?.get("action")?.asString == "permission.request" }

        assertEquals("error", lastOutgoingMessage()?.get("type")?.asString)
    }

    @Test
    fun `local state init with a positive version is served from the store`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "localState.init", payload = """{"version":2,"data":{"k":"v"}}"""))
        await { lastOutgoingMessage()?.get("action")?.asString == "localState.init" }

        assertEquals("response", lastOutgoingMessage()?.get("type")?.asString)
        assertEquals("v", lastOutgoingPayload()!!.getAsJsonObject("data").get("k").asString)
    }

    @Test
    fun `sync operation answers the page with the operation response`() {
        mockkObject(MindboxEventManager)
        every {
            MindboxEventManager.syncOperation(any(), any(), any(), any())
        } answers {
            thirdArg<(String) -> Unit>().invoke("""{"status":"Success"}""")
        }
        startAndAwaitPageLoad()

        postFromPage(request(action = "syncOperation", payload = """{"operation":"op.sync","body":{}}"""))
        await { lastOutgoingMessage()?.get("action")?.asString == "syncOperation" }

        assertEquals("response", lastOutgoingMessage()?.get("type")?.asString)
        assertEquals("Success", lastOutgoingPayload()!!.get("status").asString)
        unmockkObject(MindboxEventManager)
    }

    @Test
    fun `async operation from the block reaches the event manager`() {
        mockkObject(MindboxEventManager)
        every {
            MindboxEventManager.asyncOperation(any(), any(), any<String>())
        } just runs
        startAndAwaitPageLoad()

        postFromPage(
            request(action = "asyncOperation", payload = """{"operation":"op.name","body":{}}""")
        )
        await { lastOutgoingMessage()?.get("action")?.asString == "asyncOperation" }

        verify(exactly = 1) {
            MindboxEventManager.asyncOperation(any(), "op.name", any<String>())
        }
        unmockkObject(MindboxEventManager)
    }

    @Test
    fun `initDataUpdated refreshes the feed without recreating the webview`() {
        startAndAwaitPageLoad()
        val viewBeforeUpdate = webView

        var updateResult: Boolean? = null
        holder.updateParams(mapOf("stories" to """[{"inAppId":"story-2"}]""")) { isUpdated ->
            updateResult = isUpdated
        }
        await { lastOutgoingMessage()?.get("action")?.asString == "initDataUpdated" }

        val outgoing = lastOutgoingMessage()!!
        // The push carries the whole start payload — the same envelope `ready` is answered with —
        // not only the params (contract shared with iOS).
        val payload = lastOutgoingPayload()!!
        assertTrue(payload.get("stories").isJsonArray)
        assertEquals(
            "story-2",
            payload.getAsJsonArray("stories").get(0).asJsonObject.get("inAppId").asString
        )
        assertEquals("endpoint-id", payload.get("endpointId").asString)

        // The page answers success — the very same webview keeps living.
        postFromPage(
            """{"type":"response","action":"initDataUpdated","payload":"{\"success\":true}",""" +
                """"id":${outgoing.get("id")},"version":1,"timestamp":2}"""
        )
        await { updateResult != null }
        assertEquals(true, updateResult)
        assertTrue(viewBeforeUpdate === webView)
    }

    @Test
    fun `an action the block does not perform is acknowledged, not refused`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "click", payload = "{}"))
        await { lastOutgoingMessage()?.get("action")?.asString == "click" }

        // In sync with iOS: a block has no window to click through, and "nothing to do here" is
        // an outcome the page carries on from. Nothing about the block changes.
        assertEquals("response", lastOutgoingMessage()?.get("type")?.asString)
        assertTrue(states.none { state -> state == EmbeddedBlockState.Failed })
    }

    @Test
    fun `a question the block cannot answer is refused by name`() {
        startAndAwaitPageLoad()

        // `close` a block would acknowledge; an operation it cannot run is a different half of the
        // rule — the page hears a refusal instead of a success it would take for done.
        postFromPage(request(action = "navigationIntercepted", payload = "{}"))
        await { lastOutgoingMessage()?.get("action")?.asString == "navigationIntercepted" }

        assertEquals("error", lastOutgoingMessage()?.get("type")?.asString)
        assertTrue(states.none { state -> state == EmbeddedBlockState.Failed })
    }

    @Test
    fun `content fetch failure reports Failed`() {
        coEvery { gatewayManager.fetchWebViewContent(any()) } throws IllegalStateException("network down")

        holder.start()

        await { states.lastOrNull() == EmbeddedBlockState.Failed }
    }

    @Test
    fun `updateParams before the page exists reports failure`() {
        var updateResult: Boolean? = null

        holder.updateParams(mapOf("stories" to "[]")) { isUpdated -> updateResult = isUpdated }

        assertEquals(false, updateResult)
    }

    @Test
    fun `local state get is served from the store`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "localState.get", payload = """{"keys":["inapp.completed.story-1"]}"""))
        await { lastOutgoingMessage()?.get("action")?.asString == "localState.get" }

        assertEquals("response", lastOutgoingMessage()?.get("type")?.asString)
    }

    @Test
    fun `a refusal off screen is held until the block comes back`() {
        coEvery { inAppInteractor.recordBlockShow(any(), any(), any()) } just runs
        startAndAwaitPageLoad()

        holder.pause()
        postFromPage(request(action = "contentRendered", payload = """{"count":"many"}"""))

        // The backend hears about blocks the user was shown, and this page failed behind another
        // screen.
        verify(exactly = 0) { inAppFailureTracker.sendFailure(any(), any(), any(), any()) }

        holder.start()

        verify(exactly = 1) {
            inAppFailureTracker.sendFailure("embedded-id", FailureReason.PRESENTATION_FAILED, any(), any())
        }
    }

    @Test
    fun `a page that rendered off screen counts its show when the block comes back`() {
        coEvery { inAppInteractor.recordBlockShow(any(), any(), any()) } just runs
        startAndAwaitPageLoad()

        holder.pause()
        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))

        coVerify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any()) }

        holder.start()

        coVerify(exactly = 1, timeout = 5_000L) {
            inAppInteractor.recordBlockShow("embedded-id", any(), any())
        }
    }

    @Test
    fun `the counted show carries the time the render took, not the time off screen`() {
        coEvery { inAppInteractor.recordBlockShow(any(), any(), any()) } just runs
        startAndAwaitPageLoad()

        holder.pause()
        elapsed = 1_000L
        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))
        // Two minutes on another screen, which is not time the user spent waiting for this page.
        elapsed = 121_000L

        holder.start()

        coVerify(exactly = 1, timeout = 5_000L) {
            inAppInteractor.recordBlockShow("embedded-id", Milliseconds(1_000L), any())
        }
    }

    @Test
    fun `release destroys the webview and start after release does nothing`() {
        startAndAwaitPageLoad()
        val statesBefore = states.size

        holder.release()
        holder.start()

        assertEquals(statesBefore, states.size)
    }
}
