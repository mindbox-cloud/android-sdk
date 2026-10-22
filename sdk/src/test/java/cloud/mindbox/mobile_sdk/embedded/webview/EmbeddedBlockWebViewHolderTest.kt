package cloud.mindbox.mobile_sdk.embedded.webview

import cloud.mindbox.mobile_sdk.utils.setMindboxScope
import kotlinx.coroutines.SupervisorJob
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Looper
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.di.modules.AppModule
import cloud.mindbox.mobile_sdk.di.modules.DataModule
import cloud.mindbox.mobile_sdk.embedded.EmbeddedBlockState
import cloud.mindbox.mobile_sdk.embedded.MindboxEmbeddedBlockFailReason
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.InAppInteractorImpl
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionStatus
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFailureTracker
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppWebViewCachePolicy
import cloud.mindbox.mobile_sdk.inapp.presentation.view.BridgeMessage
import cloud.mindbox.mobile_sdk.inapp.presentation.view.MindboxWebPageRegistry
import cloud.mindbox.mobile_sdk.inapp.presentation.view.WebViewAction
import cloud.mindbox.mobile_sdk.inapp.presentation.view.stubBridgeHelpers
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.inapp.domain.models.Frequency
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.PlaceKey
import cloud.mindbox.mobile_sdk.models.SessionEpoch
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.models.TrackVisitOrigin
import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason
import cloud.mindbox.mobile_sdk.utils.Constants
import cloud.mindbox.mobile_sdk.utils.SystemTimeProvider
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import com.google.gson.JsonObject
import cloud.mindbox.mobile_sdk.inapp.presentation.OnShowInAppOutcome
import cloud.mindbox.mobile_sdk.inapp.presentation.ShowInAppOutcome
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.slot
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.json.JSONTokener
import org.junit.After
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.presentation.view.BridgeErrorCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
class EmbeddedBlockWebViewHolderTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val realGson = DataModule(mockk(relaxed = true), mockk(relaxed = true)).gson

    private val gatewayManager: GatewayManager = mockk()
    private val inAppInteractor: InAppInteractor = mockk()
    private val inAppMessageManager: InAppMessageManager = mockk(relaxUnitFun = true)
    private val webPageRegistry: MindboxWebPageRegistry = mockk(relaxUnitFun = true)
    private val inAppFailureTracker: InAppFailureTracker = mockk(relaxed = true)

    private var elapsed = 0L
    private val timeProvider: SystemTimeProvider = mockk {
        every { elapsedSince(any()) } answers { Milliseconds(elapsed) }
        every { monotonicMillis() } answers { Milliseconds(elapsed) }
        every { monotonicElapsedSince(any()) } answers { Milliseconds(elapsed) }
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
            stubBridgeHelpers(application)
        }
        mockkObject(DbManager)
        every { DbManager.listenConfigurations() } returns flowOf(
            mockk<Configuration>(relaxed = true) {
                every { endpointId } returns "endpoint-id"
                every { versionName } returns "1.0"
            }
        )
        coEvery { gatewayManager.fetchWebViewContent(any()) } returns "<html>block</html>"
        coEvery { inAppInteractor.awaitReturnChecked() } just runs
        every { inAppInteractor.returnCheckPending } returns MutableStateFlow(false)

        holder = blockOn(application)
    }

    private fun blockOn(
        context: Context,
        ackBudget: Milliseconds = Constants.WebView.readyTimeout,
        selectionTime: Milliseconds = Milliseconds(0L),
    ): EmbeddedBlockWebViewHolder =
        EmbeddedBlockWebViewHolder(
            inAppId = "embedded-id",
            placeSystemName = PlaceKey.of("main-screen-top"),
            layer = InAppStub.getEmbeddedWebViewLayer(),
            context = context,
            frequency = Frequency(Frequency.Delay.Unlimited),
            tags = null,
            selectionTime = selectionTime,
            ackBudget = ackBudget,
        ).apply {
            confirmForSession(SessionEpoch(0L))
            onStateChange = { state -> states.add(state) }
        }

    @After
    fun tearDown() {
        holder.release()
        unmockkObject(DbManager)
    }

    private val webView: WebView
        get() = holder.run {
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
        assertEquals("<html>block</html>", loaded?.data)
        assertEquals("https://blocks.local/base", loaded?.baseUrl)
    }

    @Test
    fun `block webview has native scroll bars disabled`() {
        startAndAwaitPageLoad()

        assertFalse(webView.isHorizontalScrollBarEnabled)
        assertFalse(webView.isVerticalScrollBarEnabled)
    }

    @Test
    fun `ready response contains items as json array not string`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "ready", payload = "{}"))
        await { lastOutgoingMessage()?.get("action")?.asString == "ready" }

        val payload = lastOutgoingPayload()!!
        assertTrue(payload.get("items").isJsonArray)
        assertEquals(
            "inapp-1",
            payload.getAsJsonArray("items").get(0).asJsonObject.get("inAppId").asString
        )
        assertEquals("endpoint-id", payload.get("endpointId").asString)
    }

    @Test
    fun `filterShowableInapps returns subset from the interactor`() {
        coEvery { inAppInteractor.filterShowableInAppIds("embedded-id", listOf("inapp-1", "inapp-2")) } returns
            listOf("inapp-1")
        startAndAwaitPageLoad()

        postFromPage(request(action = "filterShowableInapps", payload = """{"inappIds":["inapp-1","inapp-2"]}"""))
        await { lastOutgoingMessage()?.get("action")?.asString == "filterShowableInapps" }

        val payload = lastOutgoingPayload()!!
        assertEquals(1, payload.getAsJsonArray("inappIds").size())
        assertEquals("inapp-1", payload.getAsJsonArray("inappIds").get(0).asString)
    }

    @Test
    fun `filterShowableInapps without an inappIds array is refused with an error`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "filterShowableInapps", payload = "{}"))
        await { lastOutgoingMessage()?.get("action")?.asString == "filterShowableInapps" }

        assertEquals("error", lastOutgoingMessage()!!.get("type").asString)
        assertEquals("invalid_payload", lastOutgoingPayload()!!.get("error").asString)
        coVerify(exactly = 0) { inAppInteractor.filterShowableInAppIds(any(), any()) }
    }

    @Test
    fun `filterShowableInapps the SDK failed to check is answered with internal_error, not the failure text`() {
        coEvery { inAppInteractor.filterShowableInAppIds(any(), any()) } throws IllegalStateException("config repository is down")
        startAndAwaitPageLoad()

        postFromPage(request(action = "filterShowableInapps", payload = """{"inappIds":["inapp-1"]}"""))
        await { lastOutgoingMessage()?.get("action")?.asString == "filterShowableInapps" }

        assertEquals("error", lastOutgoingMessage()!!.get("type").asString)
        assertEquals("internal_error", lastOutgoingPayload()!!.get("error").asString)
    }

    @Test
    fun `filterShowableInapps skips non-string ids and answers the rest`() {
        coEvery { inAppInteractor.filterShowableInAppIds("embedded-id", listOf("inapp-1")) } returns listOf("inapp-1")
        startAndAwaitPageLoad()

        postFromPage(request(action = "filterShowableInapps", payload = """{"inappIds":["inapp-1",7]}"""))
        await { lastOutgoingMessage()?.get("action")?.asString == "filterShowableInapps" }

        assertEquals("response", lastOutgoingMessage()!!.get("type").asString)
        assertEquals("inapp-1", lastOutgoingPayload()!!.getAsJsonArray("inappIds").get(0).asString)
    }

    @Test
    fun `contentRendered with positive count switches state to Ready and counts the show`() {
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        startAndAwaitPageLoad()

        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))

        await { states.lastOrNull() == EmbeddedBlockState.Ready }
        assertTrue(holder.contentView != null)
        verify(timeout = 5_000L) { inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), any(), any(), any()) }
    }

    @Test
    fun `contentRendered off screen waits for the block to return before counting the show`() {
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        startAndAwaitPageLoad()

        holder.pause()
        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))
        await { lastOutgoingMessage()?.get("action")?.asString == "contentRendered" }

        verify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) }

        holder.start()

        verify(exactly = 1, timeout = 5_000L) { inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), any(), any(), any()) }
    }

    @Test
    fun `a show whose recording died with the sdk scope is retried when the block returns`() {
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        val originalScope = Mindbox.mindboxScope
        try {
            startAndAwaitPageLoad()
            setMindboxScope(CoroutineScope(Dispatchers.Unconfined).also { it.cancel() })

            elapsed = 1_000L
            postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))
            await { lastOutgoingMessage()?.get("action")?.asString == "contentRendered" }
            verify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) }

            setMindboxScope(originalScope)
            elapsed = 61_000L
            holder.pause()
            holder.start()

            verify(exactly = 1, timeout = 5_000L) {
                inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), Milliseconds(1_000L), any(), any())
            }
        } finally {
            setMindboxScope(originalScope)
        }
    }

    @Test
    fun `a page that reports itself again after a data push keeps the time of its first render`() {
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        val originalScope = Mindbox.mindboxScope
        try {
            startAndAwaitPageLoad()
            setMindboxScope(CoroutineScope(Dispatchers.Unconfined).also { it.cancel() })
            elapsed = 1_000L
            postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))
            await { lastOutgoingMessage()?.get("action")?.asString == "contentRendered" }
            setMindboxScope(originalScope)

            holder.updateParams(mapOf("items" to "[]")) {}
            elapsed = 5_000L
            postFromPage(request(action = "contentRendered", payload = """{"count":3}""", id = "req-2"))

            verify(exactly = 1, timeout = 5_000L) {
                inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), Milliseconds(1_000L), any(), any())
            }
        } finally {
            setMindboxScope(originalScope)
        }
    }

    @Test
    fun `contentRendered with zero count switches state to Empty and reports nothing`() {
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        startAndAwaitPageLoad()

        postFromPage(request(action = "contentRendered", payload = """{"count":0}"""))

        await { states.lastOrNull() == EmbeddedBlockState.Empty }
        verify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `contentRendered without a readable count fails the block and refuses the page`() {
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        startAndAwaitPageLoad()

        postFromPage(request(action = "contentRendered", payload = """{"count":"many"}"""))

        await { states.lastOrNull() == EmbeddedBlockState.Failed(MindboxEmbeddedBlockFailReason.INTERNAL_ERROR) }
        verify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) }
        assertEquals("error", lastOutgoingMessage()!!.get("type").asString)
        assertEquals("invalid_payload", lastOutgoingPayload()!!.get("error").asString)
    }

    @Test
    fun `contentRendered with a negative count fails the block instead of passing for empty`() {
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        startAndAwaitPageLoad()

        postFromPage(request(action = "contentRendered", payload = """{"count":-1}"""))

        await { states.lastOrNull() == EmbeddedBlockState.Failed(MindboxEmbeddedBlockFailReason.INTERNAL_ERROR) }
        verify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) }
        assertEquals("error", lastOutgoingMessage()!!.get("type").asString)
        assertEquals("invalid_payload", lastOutgoingPayload()!!.get("error").asString)
    }

    @Test
    fun `contentRendered with a fractional count is refused rather than rounded`() {
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        startAndAwaitPageLoad()

        postFromPage(request(action = "contentRendered", payload = """{"count":2.5}"""))

        await { states.lastOrNull() == EmbeddedBlockState.Failed(MindboxEmbeddedBlockFailReason.INTERNAL_ERROR) }
        verify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) }
        assertEquals("error", lastOutgoingMessage()!!.get("type").asString)
        assertEquals("invalid_payload", lastOutgoingPayload()!!.get("error").asString)
    }

    @Test
    fun `contentRendered with a whole double count is a valid report`() {
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        startAndAwaitPageLoad()

        postFromPage(request(action = "contentRendered", payload = """{"count":3.0}"""))

        await { states.lastOrNull() == EmbeddedBlockState.Ready }
        verify(timeout = 5_000L) { inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), any(), any(), any()) }
    }

    @Test
    fun `the show is accounted with the refreshed snapshot`() {
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        startAndAwaitPageLoad()

        val refreshed = Frequency(Frequency.Delay.OneTimePerSession)
        holder.refreshMetricsSnapshot(refreshed, mapOf("k" to "v"))
        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))

        verify(exactly = 1, timeout = 5_000L) {
            inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", refreshed, any(), any(), any())
        }
    }

    @Test
    fun `the show is reported once per content instance`() {
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        startAndAwaitPageLoad()

        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))
        await { states.lastOrNull() == EmbeddedBlockState.Ready }
        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))

        verify(exactly = 1, timeout = 5_000L) { inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), any(), any(), any()) }
    }

    @Test
    fun `a repeated contentRendered with a zero count does not un-show a shown block`() {
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        startAndAwaitPageLoad()
        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))
        await { states.lastOrNull() == EmbeddedBlockState.Ready }

        postFromPage(request(action = "contentRendered", payload = """{"count":0}""", id = "again"))
        await { lastOutgoingMessage()?.get("id")?.asString == "again" }

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals("response", lastOutgoingMessage()!!.get("type").asString)
    }

    @Test
    fun `a repeated contentRendered without a readable count is ignored once the block is shown`() {
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        startAndAwaitPageLoad()
        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))
        await { states.lastOrNull() == EmbeddedBlockState.Ready }

        postFromPage(request(action = "contentRendered", payload = """{"count":"many"}""", id = "again"))
        await { lastOutgoingMessage()?.get("id")?.asString == "again" }

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertEquals("response", lastOutgoingMessage()!!.get("type").asString)
        verify(exactly = 0) {
            MindboxDI.appModule.inAppFailureTracker.sendBlockFailure(any(), any(), any(), any())
        }
    }

    private val outcomes = slot<OnShowInAppOutcome>()

    private fun givenShowPathCapturesOutcome() {
        every { inAppMessageManager.showInAppById(any(), any(), any(), capture(outcomes)) } just runs
    }

    private fun noShowInAppAnswerYet(): Boolean {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(150)
        shadowOf(Looper.getMainLooper()).idle()
        return lastOutgoingMessage()?.get("action")?.asString != "showInApp"
    }

    @Test
    fun `showInApp is answered with success only once the window is on screen`() {
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        givenShowPathCapturesOutcome()
        startAndAwaitPageLoad()

        postFromPage(
            request(action = "showInApp", payload = """{"inappId":"inapp-1","params":{}}""")
        )
        await { outcomes.isCaptured }
        assertTrue(noShowInAppAnswerYet())

        outcomes.captured.onOutcome(ShowInAppOutcome.Shown)
        await { lastOutgoingMessage()?.get("action")?.asString == "showInApp" }

        assertEquals("response", lastOutgoingMessage()?.get("type")?.asString)
        assertTrue(lastOutgoingPayload()!!.get("success").asBoolean)
        verify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `showInApp from a page whose block withholds its show opens what the user taps`() {
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        givenShowPathCapturesOutcome()
        startAndAwaitPageLoad()
        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))
        await { states.lastOrNull() == EmbeddedBlockState.Ready }
        holder.withholdShow(true)

        postFromPage(request(action = "showInApp", payload = """{"inappId":"inapp-1","params":{}}""", id = "tap"))
        await { outcomes.isCaptured }
        outcomes.captured.onOutcome(ShowInAppOutcome.Shown)
        await { lastOutgoingMessage()?.get("id")?.asString == "tap" }

        verify(exactly = 1) { inAppMessageManager.showInAppById("inapp-1", any(), any(), any()) }
        assertEquals("response", lastOutgoingMessage()?.get("type")?.asString)
        assertTrue(lastOutgoingPayload()!!.get("success").asBoolean)
    }

    @Test
    fun `showInApp whose show failed after it started is answered with show_failed, once`() {
        givenShowPathCapturesOutcome()
        startAndAwaitPageLoad()

        postFromPage(request(action = "showInApp", payload = """{"inappId":"inapp-1"}"""))
        await { outcomes.isCaptured }
        outcomes.captured.onOutcome(ShowInAppOutcome.NotShown(BridgeErrorCode.SHOW_FAILED))
        await { lastOutgoingMessage()?.get("action")?.asString == "showInApp" }
        assertEquals("error", lastOutgoingMessage()?.get("type")?.asString)
        assertEquals("show_failed", lastOutgoingPayload()!!.get("error").asString)

        val answered = shadowOf(webView).lastEvaluatedJavascript
        outcomes.captured.onOutcome(ShowInAppOutcome.Shown)
        assertTrue(noShowInAppAnswerYet() || shadowOf(webView).lastEvaluatedJavascript == answered)
    }

    @Test
    fun `releasing the block while showInApp waits answers nothing`() {
        givenShowPathCapturesOutcome()
        startAndAwaitPageLoad()

        postFromPage(request(action = "showInApp", payload = """{"inappId":"inapp-1"}"""))
        await { outcomes.isCaptured }
        val page = shadowOf(webView)
        val lastScriptBeforeRelease = page.lastEvaluatedJavascript
        holder.release()
        outcomes.captured.onOutcome(ShowInAppOutcome.Shown)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(150)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(lastScriptBeforeRelease, page.lastEvaluatedJavascript)
    }

    private val requesterIsActive = slot<() -> Boolean>()

    private fun givenShowPathCapturesRequester() {
        every { inAppMessageManager.showInAppById(any(), any(), capture(requesterIsActive), capture(outcomes)) } just runs
    }

    @Test
    fun `a block that left the screen is no longer the requester of its showInApp and its page hears not_visible`() {
        givenShowPathCapturesRequester()
        startAndAwaitPageLoad()
        postFromPage(request(action = "showInApp", payload = """{"inappId":"inapp-1"}"""))
        await { outcomes.isCaptured }
        assertTrue(requesterIsActive.captured())

        holder.pause()
        assertFalse(requesterIsActive.captured())
        outcomes.captured.onOutcome(ShowInAppOutcome.NotShown(BridgeErrorCode.NOT_VISIBLE))
        await { lastOutgoingMessage()?.get("action")?.asString == "showInApp" }

        assertEquals("error", lastOutgoingMessage()?.get("type")?.asString)
        assertEquals("not_visible", lastOutgoingPayload()!!.get("error").asString)
    }

    @Test
    fun `a released block is no longer the requester of the showInApp it sent`() {
        givenShowPathCapturesRequester()
        startAndAwaitPageLoad()
        postFromPage(request(action = "showInApp", payload = """{"inappId":"inapp-1"}"""))
        await { requesterIsActive.isCaptured }

        holder.release()

        assertFalse(requesterIsActive.captured())
    }

    @Test
    fun `a block whose screen is closing is no longer the requester of the showInApp it sent`() {
        val screen = Robolectric.buildActivity(Activity::class.java).setup().get()
        rebuildHolder(context = screen)
        givenShowPathCapturesRequester()
        startAndAwaitPageLoad()
        postFromPage(request(action = "showInApp", payload = """{"inappId":"inapp-1"}"""))
        await { requesterIsActive.isCaptured }
        assertTrue(requesterIsActive.captured())

        screen.finish()

        assertFalse(requesterIsActive.captured())
    }

    @Test
    fun `a block whose screen only went to the background is still the requester of the showInApp it sent`() {
        val screen = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        rebuildHolder(context = screen.get())
        givenShowPathCapturesRequester()
        startAndAwaitPageLoad()
        postFromPage(request(action = "showInApp", payload = """{"inappId":"inapp-1"}"""))
        await { requesterIsActive.isCaptured }

        screen.pause()

        assertTrue(requesterIsActive.captured())
    }

    @Test
    fun `showInApp hands the id and the params to the show path`() {
        givenShowPathCapturesOutcome()
        startAndAwaitPageLoad()

        postFromPage(
            request(
                action = "showInApp",
                payload = """{"inappId":"inapp-1","params":{"title":"Заголовок 1","record":{"rank":3}}}"""
            )
        )
        await { outcomes.isCaptured }

        verify(exactly = 1) {
            inAppMessageManager.showInAppById(
                "inapp-1",
                mapOf(
                    "title" to JsonPrimitive("Заголовок 1"),
                    "record" to JsonParser.parseString("""{"rank":3}"""),
                ),
                any(),
                any()
            )
        }
    }

    @Test
    fun `showInApp sent with an object payload works the same as a string one`() {
        givenShowPathCapturesOutcome()
        startAndAwaitPageLoad()

        postFromPage(
            """{"type":"request","action":"showInApp","payload":{"inappId":"inapp-1","params":{"a":1}},"id":"req-obj","version":1,"timestamp":1}"""
        )
        await { outcomes.isCaptured }

        verify(exactly = 1) {
            inAppMessageManager.showInAppById("inapp-1", mapOf("a" to JsonPrimitive(1)), any(), any())
        }
    }

    @Test
    fun `showInApp without an inappId is refused with an error`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "showInApp", payload = """{"params":{}}"""))
        await { lastOutgoingMessage()?.get("action")?.asString == "showInApp" }

        assertEquals("error", lastOutgoingMessage()?.get("type")?.asString)
        assertEquals("invalid_payload", lastOutgoingPayload()!!.get("error").asString)
        verify(exactly = 0) { inAppMessageManager.showInAppById(any(), any(), any(), any()) }
    }

    @Test
    fun `showInApp from a paused block is refused - nobody is looking at the page`() {
        startAndAwaitPageLoad()
        holder.pause()

        postFromPage(request(action = "showInApp", payload = """{"inappId":"inapp-1"}"""))
        await { lastOutgoingMessage()?.get("action")?.asString == "showInApp" }

        assertEquals("error", lastOutgoingMessage()?.get("type")?.asString)
        assertEquals("not_visible", lastOutgoingPayload()!!.get("error").asString)
        verify(exactly = 0) { inAppMessageManager.showInAppById(any(), any(), any(), any()) }
    }

    @Test
    fun `showInApp from a failed attempt is refused as not_visible`() {
        startAndAwaitPageLoad()
        postFromPage(request(action = "contentRendered", payload = """{"count":-1}""", id = "bad"))
        await { states.lastOrNull() == EmbeddedBlockState.Failed(MindboxEmbeddedBlockFailReason.INTERNAL_ERROR) }

        postFromPage(request(action = "showInApp", payload = """{"inappId":"inapp-1"}"""))
        await { lastOutgoingMessage()?.get("action")?.asString == "showInApp" }

        assertEquals("error", lastOutgoingMessage()?.get("type")?.asString)
        assertEquals("not_visible", lastOutgoingPayload()!!.get("error").asString)
        verify(exactly = 0) { inAppMessageManager.showInAppById(any(), any(), any(), any()) }
    }

    @Test
    fun `the old checkInappsTargeting name is not spoken anymore`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "checkInappsTargeting", payload = """{"inappIds":["inapp-1"]}""", id = "old-name-1"))
        await { lastOutgoingMessage()?.get("id")?.asString == "old-name-1" }

        coVerify(exactly = 0) { inAppInteractor.filterShowableInAppIds(any(), any()) }
        assertEquals("""{"error":"unknown_action"}""", lastOutgoingMessage()?.get("payload")?.asString)
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
    fun `local state set answers the author and is broadcast to every other page`() {
        startAndAwaitPageLoad()

        postFromPage(
            request(action = "localState.set", payload = """{"data":{"inapp.completed.inapp-1":"rev-1"}}""")
        )
        await { lastOutgoingMessage()?.get("action")?.asString == "localState.set" }

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
                assertEquals("code for $action", "not_visible", lastOutgoingPayload()!!.get("error").asString)
            }
    }

    @Test
    fun `openLink with an unusable payload is refused`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "openLink", payload = "{}"))
        await { lastOutgoingMessage()?.get("action")?.asString == "openLink" }

        assertEquals("error", lastOutgoingMessage()?.get("type")?.asString)
        assertEquals("invalid_payload", lastOutgoingPayload()!!.get("error").asString)
    }

    @Test
    fun `haptic with an unusable payload is swallowed with an empty answer`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "haptic", payload = "{}"))
        await { lastOutgoingMessage()?.get("action")?.asString == "haptic" }

        assertEquals("response", lastOutgoingMessage()?.get("type")?.asString)
    }

    @Test
    fun `motion start with a known gesture but no accelerometer is refused`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "motion.start", payload = """{"gestures":["shake"]}"""))
        await { lastOutgoingMessage()?.get("action")?.asString == "motion.start" }

        assertEquals("error", lastOutgoingMessage()?.get("type")?.asString)
        assertEquals("gestures_unavailable", lastOutgoingPayload()!!.get("error").asString)
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
            MindboxEventManager.syncOperation("op.sync", "{}", any(), any())
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
    fun `initDataUpdated sends the whole start payload and refreshes the page without recreating the webview, as on iOS`() {
        startAndAwaitPageLoad()
        val viewBeforeUpdate = webView

        var updateResult: DataPushOutcome? = null
        holder.updateParams(mapOf("items" to """[{"inAppId":"inapp-2"}]""")) { outcome ->
            updateResult = outcome
        }
        await { lastOutgoingMessage()?.get("action")?.asString == "initDataUpdated" }

        val outgoing = lastOutgoingMessage()!!
        val payload = lastOutgoingPayload()!!
        assertTrue(payload.get("items").isJsonArray)
        assertEquals(
            "inapp-2",
            payload.getAsJsonArray("items").get(0).asJsonObject.get("inAppId").asString
        )
        assertEquals("endpoint-id", payload.get("endpointId").asString)

        postFromPage(
            """{"type":"response","action":"initDataUpdated","payload":"{\"success\":true}",""" +
                """"id":${outgoing.get("id")},"version":1,"timestamp":2}"""
        )
        await { updateResult != null }
        assertEquals(DataPushOutcome.CONFIRMED, updateResult)
        assertTrue(viewBeforeUpdate === webView)
    }

    private fun rebuildHolder(
        context: Context = application,
        ackBudget: Milliseconds = Constants.WebView.readyTimeout,
        selectionTime: Milliseconds = Milliseconds(0L),
    ) {
        holder.release()
        states.clear()
        holder = blockOn(context, ackBudget, selectionTime)
    }

    private fun answerDataPush(id: com.google.gson.JsonElement) {
        postFromPage(
            """{"type":"response","action":"initDataUpdated","payload":"{\"success\":true}",""" +
                """"id":$id,"version":1,"timestamp":2}"""
        )
    }

    @Test
    fun `a data push confirmed off screen still succeeds`() {
        startAndAwaitPageLoad()
        holder.pause()

        var updateResult: DataPushOutcome? = null
        holder.updateParams(mapOf("items" to "[]")) { outcome -> updateResult = outcome }
        await { lastOutgoingMessage()?.get("action")?.asString == "initDataUpdated" }

        answerDataPush(lastOutgoingMessage()!!.get("id"))

        await { updateResult != null }
        assertEquals(DataPushOutcome.CONFIRMED, updateResult)
    }

    @Test
    fun `a data push does not spend its ack budget off screen`() {
        rebuildHolder(ackBudget = Milliseconds(1_000L))
        startAndAwaitPageLoad()
        holder.pause()

        var updateResult: DataPushOutcome? = null
        holder.updateParams(mapOf("items" to "[]")) { outcome -> updateResult = outcome }
        await { lastOutgoingMessage()?.get("action")?.asString == "initDataUpdated" }
        val push = lastOutgoingMessage()!!

        Thread.sleep(2_000L)
        assertNull(updateResult)

        holder.start()
        answerDataPush(push.get("id"))

        await { updateResult != null }
        assertEquals(DataPushOutcome.CONFIRMED, updateResult)
    }

    @Test
    fun `the ack budget is spent by watched time and survives a pause`() {
        rebuildHolder(ackBudget = Milliseconds(1_000L))
        startAndAwaitPageLoad()

        var updateResult: DataPushOutcome? = null
        holder.updateParams(mapOf("items" to "[]")) { outcome -> updateResult = outcome }
        await { lastOutgoingMessage()?.get("action")?.asString == "initDataUpdated" }

        holder.pause()
        Thread.sleep(2_000L)
        assertNull(updateResult)

        holder.start()

        await { updateResult != null }
        assertEquals(DataPushOutcome.REFUSED, updateResult)
    }

    @Test
    fun `a second data push abandons the first wait`() {
        startAndAwaitPageLoad()

        var firstResult: DataPushOutcome? = null
        holder.updateParams(mapOf("items" to "[]")) { outcome -> firstResult = outcome }
        await { lastOutgoingMessage()?.get("action")?.asString == "initDataUpdated" }

        var secondResult: DataPushOutcome? = null
        holder.updateParams(mapOf("items" to """[{"inAppId":"inapp-2"}]""")) { outcome ->
            secondResult = outcome
        }
        await { firstResult != null }
        assertEquals(DataPushOutcome.ABANDONED, firstResult)

        await {
            lastOutgoingPayload()?.getAsJsonArray("items")?.any { item ->
                item.asJsonObject.get("inAppId")?.asString == "inapp-2"
            } == true
        }
        answerDataPush(lastOutgoingMessage()!!.get("id"))

        await { secondResult != null }
        assertEquals(DataPushOutcome.CONFIRMED, secondResult)
    }

    @Test
    fun `a data push whose sdk scope dies while it waits for the page is reported abandoned, not refused`() {
        val originalScope = Mindbox.mindboxScope
        val dyingScope = CoroutineScope(Dispatchers.Default)
        try {
            startAndAwaitPageLoad()
            setMindboxScope(dyingScope)
            val outcomes = ConcurrentLinkedQueue<DataPushOutcome>()
            holder.updateParams(mapOf("items" to "[]")) { outcome -> outcomes.add(outcome) }
            await { lastOutgoingMessage()?.get("action")?.asString == "initDataUpdated" }

            dyingScope.cancel()

            await { outcomes.isNotEmpty() }
            letTheShowRecordingSettle()
            assertEquals(listOf(DataPushOutcome.ABANDONED), outcomes.toList())
            assertTrue(holder.isDataPushAbandoned)
        } finally {
            setMindboxScope(originalScope)
        }
    }

    @Test
    fun `a data push the page answers with an error fails at once, without waiting out its ack budget`() {
        startAndAwaitPageLoad()
        var updateResult: DataPushOutcome? = null
        holder.updateParams(mapOf("items" to "[]")) { outcome -> updateResult = outcome }
        await { lastOutgoingMessage()?.get("action")?.asString == "initDataUpdated" }
        val push = lastOutgoingMessage()!!

        postFromPage(
            """{"type":"error","action":"initDataUpdated","payload":"{\"error\":\"internal_error\"}",""" +
                """"id":${push.get("id")},"version":1,"timestamp":2}"""
        )

        await(timeoutMs = 2_000L) { updateResult != null }
        assertEquals(DataPushOutcome.REFUSED, updateResult)
    }

    @Test
    fun `an action the block does not perform is acknowledged, not refused, as on iOS`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "click", payload = "{}"))
        await { lastOutgoingMessage()?.get("action")?.asString == "click" }

        assertEquals("response", lastOutgoingMessage()?.get("type")?.asString)
        assertTrue(states.none { state -> state is EmbeddedBlockState.Failed })
    }

    @Test
    fun `a question the block cannot answer is refused by name`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "navigationIntercepted", payload = "{}"))
        await { lastOutgoingMessage()?.get("action")?.asString == "navigationIntercepted" }

        assertEquals("error", lastOutgoingMessage()?.get("type")?.asString)
        assertEquals("not_served", lastOutgoingPayload()!!.get("error").asString)
        assertTrue(states.none { state -> state is EmbeddedBlockState.Failed })
    }

    @Test
    fun `an action this SDK does not know is refused with the name the page sent`() {
        startAndAwaitPageLoad()

        listOf("show", "", "OPEN_LINK").forEachIndexed { index, action ->
            postFromPage(request(action = action, payload = "{}", id = "unknown-$index"))
            await { lastOutgoingMessage()?.get("id")?.asString == "unknown-$index" }

            assertEquals("type for '$action'", "error", lastOutgoingMessage()?.get("type")?.asString)
            assertEquals("action for '$action'", action, lastOutgoingMessage()?.get("action")?.asString)
            assertEquals("payload for '$action'", """{"error":"unknown_action"}""", lastOutgoingMessage()?.get("payload")?.asString)
        }
    }

    @Test
    fun `a request of a newer version is answered with the version of this SDK`() {
        startAndAwaitPageLoad()

        postFromPage("""{"type":"request","action":"log","payload":"{}","id":"v2-1","version":${BridgeMessage.VERSION + 1},"timestamp":1}""")
        await { lastOutgoingMessage()?.get("id")?.asString == "v2-1" }

        assertEquals(BridgeMessage.VERSION, lastOutgoingMessage()?.get("version")?.asInt)
    }

    @Test
    fun `a request without an action name or an id gets no answer`() {
        startAndAwaitPageLoad()
        val before = shadowOf(webView).lastEvaluatedJavascript

        listOf(
            """{"type":"request","payload":"{}","id":"no-action","version":1,"timestamp":1}""",
            """{"type":"request","action":5,"payload":"{}","id":"number-action","version":1,"timestamp":1}""",
            """{"type":"request","action":"show","payload":"{}","version":1,"timestamp":1}""",
            """{"type":"request","action":"show","payload":"{}","id":null,"version":1,"timestamp":1}""",
        ).forEach(::postFromPage)

        assertEquals(before, shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun `a page answer without an action is still matched to its push by id`() {
        startAndAwaitPageLoad()
        var updateResult: DataPushOutcome? = null
        holder.updateParams(mapOf("items" to "[]")) { outcome -> updateResult = outcome }
        await { lastOutgoingMessage()?.get("action")?.asString == "initDataUpdated" }

        postFromPage(
            """{"type":"response","payload":"{\"success\":true}","id":${lastOutgoingMessage()!!.get("id")},"version":1,"timestamp":2}"""
        )

        await { updateResult != null }
        assertEquals(DataPushOutcome.CONFIRMED, updateResult)
    }

    @Test
    fun `content fetch failure reports Failed`() {
        coEvery { gatewayManager.fetchWebViewContent(any()) } throws IllegalStateException("network down")

        holder.start()

        await { states.lastOrNull() == EmbeddedBlockState.Failed(MindboxEmbeddedBlockFailReason.NETWORK_ERROR) }
    }

    @Test
    fun `updateParams before the page exists reports failure`() {
        var updateResult: DataPushOutcome? = null

        holder.updateParams(mapOf("items" to "[]")) { outcome -> updateResult = outcome }

        assertEquals(DataPushOutcome.REFUSED, updateResult)
    }

    @Test
    fun `local state get is served from the store`() {
        startAndAwaitPageLoad()

        postFromPage(request(action = "localState.get", payload = """{"keys":["inapp.completed.inapp-1"]}"""))
        await { lastOutgoingMessage()?.get("action")?.asString == "localState.get" }

        assertEquals("response", lastOutgoingMessage()?.get("type")?.asString)
    }

    @Test
    fun `a refusal off screen is held until the block comes back`() {
        coEvery { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        startAndAwaitPageLoad()

        holder.pause()
        postFromPage(request(action = "contentRendered", payload = """{"count":"many"}"""))

        verify(exactly = 0) { inAppFailureTracker.sendBlockFailure(any(), any(), any(), any()) }

        holder.start()

        verify(exactly = 1) {
            inAppFailureTracker.sendBlockFailure("embedded-id", FailureReason.PRESENTATION_FAILED, any(), any())
        }
    }

    @Test
    fun `a page that renders nothing while its block is away holds no live attempt, though the block hears it only once it is back`() {
        startAndAwaitPageLoad()
        assertTrue(holder.isAttemptAlive)
        holder.pause()

        postFromPage(request(action = "contentRendered", payload = """{"count":0}"""))

        assertFalse(holder.isAttemptAlive)
        assertFalse(EmbeddedBlockState.Empty in states)
        holder.start()
        assertEquals(EmbeddedBlockState.Empty, states.last())
    }

    @Test
    fun `a page whose content fails to load while its block is away holds no live attempt`() {
        val fetched = CompletableDeferred<String>()
        coEvery { gatewayManager.fetchWebViewContent(any()) } coAnswers { fetched.await() }
        holder.start()
        holder.pause()

        fetched.completeExceptionally(IllegalStateException("network down"))

        await { !holder.isAttemptAlive }
        assertTrue(states.none { state -> state is EmbeddedBlockState.Failed })
    }

    @Test
    fun `the counted show is the selection time plus the time on screen, not the time off screen`() {
        coEvery { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        rebuildHolder(selectionTime = Milliseconds(2_000L))
        startAndAwaitPageLoad()

        elapsed = 750L
        holder.pause()
        elapsed = 45_750L
        holder.start()
        elapsed = 46_000L
        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))

        coVerify(exactly = 1, timeout = 5_000L) {
            inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), Milliseconds(3_000L), any(), any())
        }
    }

    @Test
    fun `a page that rendered off screen reports every stretch on screen before the render`() {
        coEvery { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        rebuildHolder(selectionTime = Milliseconds(2_000L))
        startAndAwaitPageLoad()

        elapsed = 500L
        holder.pause()
        elapsed = 10_500L
        holder.start()
        elapsed = 11_000L
        holder.pause()
        elapsed = 41_000L
        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))
        elapsed = 121_000L
        holder.start()

        coVerify(exactly = 1, timeout = 5_000L) {
            inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), Milliseconds(3_000L), any(), any())
        }
    }

    @Test
    fun `a page counts nothing before the block first starts it`() {
        coEvery { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        rebuildHolder(selectionTime = Milliseconds(300L))

        elapsed = 20_000L
        startAndAwaitPageLoad()
        elapsed = 20_500L
        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))

        coVerify(exactly = 1, timeout = 5_000L) {
            inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), Milliseconds(800L), any(), any())
        }
    }

    private fun EmbeddedBlockWebViewHolder.retainedMarkup(): Any? {
        val field = EmbeddedBlockWebViewHolder::class.java.getDeclaredField("lastLoadedContent")
        field.isAccessible = true
        return field.get(this)
    }

    @Test
    fun `the page markup is let go once the page answers`() {
        startAndAwaitPageLoad()
        assertTrue(holder.retainedMarkup() != null)

        postFromPage(request("init", "{}"))

        assertNull(holder.retainedMarkup())
    }

    @Test
    fun `release cancels a content fetch still in flight`() {
        var isFetching = false
        var wasCancelled = false
        coEvery { gatewayManager.fetchWebViewContent(any()) } coAnswers {
            isFetching = true
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                wasCancelled = true
            }
        }
        holder.start()
        await { isFetching }

        holder.release()

        await { wasCancelled }
    }

    @Test
    fun `a soft re-initialization of the SDK does not cancel a block's page load`() {
        val page = CompletableDeferred<String>()
        var isFetching = false
        coEvery { gatewayManager.fetchWebViewContent(any()) } coAnswers {
            isFetching = true
            page.await()
        }
        holder.start()
        await { isFetching }

        Mindbox.mindboxScope.cancel()
        setMindboxScope(CoroutineScope(Dispatchers.Default + SupervisorJob() + Mindbox.coroutineExceptionHandler))
        page.complete("<html>block</html>")

        await { shadowOf(webView).lastLoadDataWithBaseURL != null }
    }

    @Test
    fun `release destroys the webview and start after release does nothing`() {
        startAndAwaitPageLoad()
        val statesBefore = states.size

        holder.release()
        holder.start()

        assertEquals(statesBefore, states.size)
    }

    private fun renderAndCountTheFirstShow() {
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        startAndAwaitPageLoad()
        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))
        await { states.lastOrNull() == EmbeddedBlockState.Ready }
        verify(exactly = 1, timeout = 5_000L) {
            inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), any(), any(), SessionEpoch(0L))
        }
    }

    private fun answerThePush() {
        await { lastOutgoingMessage()?.get("action")?.asString == "initDataUpdated" }
        val push = lastOutgoingMessage()!!
        postFromPage(
            """{"type":"response","action":"initDataUpdated","payload":"{\"success\":true}",""" +
                """"id":${push.get("id")},"version":1,"timestamp":2}"""
        )
    }

    private fun letTheShowRecordingSettle() {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(150)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `a page refreshed for a new session sends that session's show once it renders again`() {
        renderAndCountTheFirstShow()

        holder.refreshForSession(mapOf("items" to "[]"), sessionEpoch = SessionEpoch(1L), selectionTime = Milliseconds(0L)) {}
        answerThePush()
        postFromPage(request(action = "contentRendered", payload = """{"count":2}""", id = "after-refresh"))

        verify(exactly = 1, timeout = 5_000L) {
            inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), any(), any(), SessionEpoch(1L))
        }
    }

    @Test
    fun `a new session's data push into a dead sdk scope is reported abandoned once, and the session's show waits for the render after the data is sent again`() {
        renderAndCountTheFirstShow()
        val originalScope = Mindbox.mindboxScope
        try {
            setMindboxScope(CoroutineScope(Dispatchers.Unconfined).also { it.cancel() })
            val outcomes = mutableListOf<DataPushOutcome>()

            holder.refreshForSession(mapOf("items" to "[]"), sessionEpoch = SessionEpoch(1L), selectionTime = Milliseconds(0L)) { outcome ->
                outcomes.add(outcome)
            }

            assertEquals(listOf(DataPushOutcome.ABANDONED), outcomes)
            assertTrue(holder.isDataPushAbandoned)

            setMindboxScope(originalScope)
            holder.pause()
            holder.start()
            letTheShowRecordingSettle()
            verify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), SessionEpoch(1L)) }

            holder.refreshForSession(mapOf("items" to "[]"), sessionEpoch = SessionEpoch(1L), selectionTime = Milliseconds(0L)) {}
            assertFalse(holder.isDataPushAbandoned)
            answerThePush()
            postFromPage(request(action = "contentRendered", payload = """{"count":2}""", id = "after-refresh"))

            verify(exactly = 1, timeout = 5_000L) {
                inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), any(), any(), SessionEpoch(1L))
            }
            assertEquals(listOf(DataPushOutcome.ABANDONED), outcomes)
        } finally {
            setMindboxScope(originalScope)
        }
    }

    @Test
    fun `the new session's show waits for the render after the refresh, not for the page's answer to it`() {
        renderAndCountTheFirstShow()

        holder.refreshForSession(mapOf("items" to "[]"), sessionEpoch = SessionEpoch(1L), selectionTime = Milliseconds(0L)) {}
        answerThePush()
        holder.pause()
        holder.start()

        letTheShowRecordingSettle()
        verify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), SessionEpoch(1L)) }
        postFromPage(request(action = "contentRendered", payload = """{"count":2}""", id = "after-refresh"))
        verify(exactly = 1, timeout = 5_000L) {
            inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), any(), any(), SessionEpoch(1L))
        }
    }

    @Test
    fun `the new session's show on screen is its selection time plus the time on screen since the refresh`() {
        renderAndCountTheFirstShow()
        elapsed = 3_600_000L

        holder.refreshForSession(mapOf("items" to "[]"), sessionEpoch = SessionEpoch(1L), selectionTime = Milliseconds(400L)) {}
        answerThePush()
        elapsed = 3_600_250L
        postFromPage(request(action = "contentRendered", payload = """{"count":2}""", id = "after-refresh"))

        verify(exactly = 1, timeout = 5_000L) {
            inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), Milliseconds(650L), any(), SessionEpoch(1L))
        }
    }

    @Test
    fun `a page that renders long after it took the new session's data still sends that session's show`() {
        renderAndCountTheFirstShow()

        holder.refreshForSession(mapOf("items" to "[]"), sessionEpoch = SessionEpoch(1L), selectionTime = Milliseconds(400L)) {}
        answerThePush()
        elapsed = 60_000L
        shadowOf(Looper.getMainLooper()).idleFor(60L, TimeUnit.SECONDS)
        postFromPage(request(action = "contentRendered", payload = """{"count":2}""", id = "after-refresh"))

        verify(exactly = 1, timeout = 5_000L) {
            inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), Milliseconds(60_400L), any(), SessionEpoch(1L))
        }
    }

    @Test
    fun `a page built for a new session counts that session's show on its own first render`() {
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        holder.confirmForSession(SessionEpoch(1L))
        startAndAwaitPageLoad()

        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))

        verify(exactly = 1, timeout = 5_000L) {
            inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), any(), any(), SessionEpoch(1L))
        }
        verify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), SessionEpoch(0L)) }
    }

    @Test
    fun `a new session's data tells the page the source of the visit that started that session`() {
        val sessionStorage = SessionStorageManager(mockk(relaxed = true))
        every { MindboxDI.appModule.sessionStorageManager } returns sessionStorage
        rebuildHolder()
        renderAndCountTheFirstShow()
        sessionStorage.lastTrackVisitData = TrackVisitOrigin(
            source = "link",
            requestUrl = "https://example.com/promo",
        )
        sessionStorage.clearSessionData()

        holder.refreshForSession(mapOf("items" to "[]"), sessionEpoch = SessionEpoch(1L), selectionTime = Milliseconds(0L)) {}
        await { lastOutgoingMessage()?.get("action")?.asString == "initDataUpdated" }

        assertEquals("link", lastOutgoingPayload()!!.get("trackVisitSource").asString)
        assertEquals("https://example.com/promo", lastOutgoingPayload()!!.get("trackVisitRequestUrl").asString)
    }

    @Test
    fun `a new session that began while the block was off screen leaves the time until its return out of the show`() {
        renderAndCountTheFirstShow()
        elapsed = 1_000L
        holder.pause()

        holder.refreshForSession(mapOf("items" to "[]"), sessionEpoch = SessionEpoch(1L), selectionTime = Milliseconds(400L)) {}
        answerThePush()
        elapsed = 1_801_000L
        holder.start()
        elapsed = 1_801_200L
        postFromPage(request(action = "contentRendered", payload = """{"count":2}""", id = "after-refresh"))

        verify(exactly = 1, timeout = 5_000L) {
            inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), Milliseconds(600L), any(), SessionEpoch(1L))
        }
    }

    @Test
    fun `a page refreshed for a new session while off screen counts no time until the block is back on screen`() {
        renderAndCountTheFirstShow()
        holder.pause()

        holder.refreshForSession(mapOf("items" to "[]"), sessionEpoch = SessionEpoch(1L), selectionTime = Milliseconds(400L)) {}
        answerThePush()
        elapsed = 30_000L
        postFromPage(request(action = "contentRendered", payload = """{"count":2}""", id = "after-refresh"))
        await { lastOutgoingMessage()?.get("id")?.asString == "after-refresh" }
        elapsed = 1_800_000L
        holder.start()

        verify(exactly = 1, timeout = 5_000L) {
            inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), Milliseconds(400L), any(), SessionEpoch(1L))
        }
    }

    @Test
    fun `a refresh within the session the page already counted sends no second show`() {
        renderAndCountTheFirstShow()

        holder.refreshForSession(mapOf("items" to "[]"), sessionEpoch = SessionEpoch(0L), selectionTime = Milliseconds(0L)) {}
        answerThePush()
        postFromPage(request(action = "contentRendered", payload = """{"count":2}""", id = "after-refresh"))
        await { lastOutgoingMessage()?.get("id")?.asString == "after-refresh" }

        letTheShowRecordingSettle()
        verify(exactly = 1) { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a page refreshed for a new session from inside its first render counts only the new session's show`() {
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        var isRefreshed = false
        holder.onStateChange = { state ->
            states.add(state)
            if (state == EmbeddedBlockState.Ready && !isRefreshed) {
                isRefreshed = true
                holder.refreshForSession(mapOf("items" to "[]"), sessionEpoch = SessionEpoch(1L), selectionTime = Milliseconds(0L)) {}
            }
        }
        startAndAwaitPageLoad()
        val configurations = DbManager.listenConfigurations()
        val renderAnswered = CompletableDeferred<Unit>()
        every { DbManager.listenConfigurations() } returns flow {
            renderAnswered.await()
            emitAll(configurations)
        }

        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))
        renderAnswered.complete(Unit)
        answerThePush()
        postFromPage(request(action = "contentRendered", payload = """{"count":3}""", id = "after-refresh"))

        verify(exactly = 1, timeout = 5_000L) {
            inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), any(), any(), SessionEpoch(1L))
        }
        verify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), SessionEpoch(0L)) }
    }

    @Test
    fun `a page still loading when a new session picks it again counts that session's show from its answer, the rest of its load included`() {
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        rebuildHolder(selectionTime = Milliseconds(2_000L))
        startAndAwaitPageLoad()
        elapsed = 1_000L

        holder.scheduleSessionRefresh(sessionEpoch = SessionEpoch(1L), selectionTime = Milliseconds(500L))
        elapsed = 3_000L
        postFromPage(request(action = "contentRendered", payload = """{"count":1}"""))
        holder.refreshForSession(mapOf("items" to "[]"), sessionEpoch = SessionEpoch(1L), selectionTime = Milliseconds(500L)) {}
        answerThePush()
        elapsed = 3_250L
        postFromPage(request(action = "contentRendered", payload = """{"count":1}""", id = "after-refresh"))

        verify(exactly = 1, timeout = 5_000L) {
            inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), Milliseconds(2_750L), any(), SessionEpoch(1L))
        }
        letTheShowRecordingSettle()
        verify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), SessionEpoch(0L)) }
    }

    @Test
    fun `a show withheld while the place has nothing to show goes once the hold is lifted`() {
        renderAndCountTheFirstShow()
        holder.refreshForSession(mapOf("items" to "[]"), sessionEpoch = SessionEpoch(1L), selectionTime = Milliseconds(0L)) {}
        answerThePush()

        holder.withholdShow(true)
        postFromPage(request(action = "contentRendered", payload = """{"count":2}""", id = "after-refresh"))
        await { lastOutgoingMessage()?.get("id")?.asString == "after-refresh" }
        letTheShowRecordingSettle()
        verify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), SessionEpoch(1L)) }

        holder.withholdShow(false)

        verify(exactly = 1, timeout = 5_000L) {
            inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), any(), any(), SessionEpoch(1L))
        }
    }

    @Test
    fun `a page drawn while away whose return finds the session over sends only the new session's show`() {
        val clock = mockk<TimeProvider>(relaxed = true) {
            every { currentTimestamp() } returns Timestamp(1L)
        }
        val inAppRepository = mockk<InAppRepository>(relaxed = true)
        val sessionStorage = SessionStorageManager(clock).apply { addSessionExpirationListener {} }
        val sessionInteractor = InAppInteractorImpl(
            mobileConfigRepository = mockk(relaxed = true),
            inAppRepository = inAppRepository,
            inAppFilteringManager = mockk(relaxed = true),
            inAppEventManager = mockk(relaxed = true),
            inAppProcessingManager = mockk(relaxed = true),
            inAppABTestLogic = mockk(relaxed = true),
            inAppFrequencyManager = mockk(relaxed = true),
            showBudgetManager = mockk(relaxed = true),
            timeProvider = clock,
            sessionStorageManager = sessionStorage,
            inAppFailureTracker = mockk(relaxed = true),
        )
        val recorded = ConcurrentLinkedQueue<Pair<SessionEpoch, Boolean>>()
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } answers {
            sessionInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), secondArg(), thirdArg(), Milliseconds(0L), arg(4), SessionEpoch(arg(5)))
                .also { isSent -> recorded.add(SessionEpoch(arg(5)) to isSent) }
        }
        startAndAwaitPageLoad()
        holder.pause()
        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))
        await { lastOutgoingMessage()?.get("action")?.asString == "contentRendered" }
        sessionStorage.lastTrackVisitSendTime.set(1_000L)
        sessionStorage.state.sessionTime = 500L.milliseconds
        every { clock.currentTimeMillis() } returns 2_000L
        sessionStorage.hasSessionExpired()

        holder.start()
        await { recorded.isNotEmpty() }
        sessionStorage.clearSessionData()
        holder.refreshForSession(mapOf("items" to "[]"), sessionEpoch = SessionEpoch(1L), selectionTime = Milliseconds(0L)) {}
        answerThePush()
        postFromPage(request(action = "contentRendered", payload = """{"count":2}""", id = "after-refresh"))
        await { recorded.size == 2 }

        assertEquals(listOf(SessionEpoch(0L) to false, SessionEpoch(1L) to true), recorded.toList())
        verify(exactly = 1) { inAppRepository.sendInAppShown("embedded-id", any(), any()) }
    }

    @Test
    fun `a show due while the app is away waits for the session check of its return`() {
        val returnChecked = CompletableDeferred<Unit>()
        every { inAppInteractor.returnCheckPending } returns MutableStateFlow(true)
        coEvery { inAppInteractor.awaitReturnChecked() } coAnswers { returnChecked.await() }
        val recordedSessions = ConcurrentLinkedQueue<SessionEpoch>()
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } answers {
            recordedSessions.add(SessionEpoch(arg(5)))
            true
        }
        startAndAwaitPageLoad()

        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))
        await { states.lastOrNull() == EmbeddedBlockState.Ready }
        letTheShowRecordingSettle()

        verify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) }

        returnChecked.complete(Unit)

        await { recordedSessions.isNotEmpty() }
        verify(exactly = 1) {
            inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), any(), any(), SessionEpoch(0L))
        }
    }

    @Test
    fun `a show waiting for the session check of the return does not go once the place lost its content meanwhile, and goes once content returns`() {
        val returnChecked = CompletableDeferred<Unit>()
        val isReturnCheckPending = MutableStateFlow(true)
        every { inAppInteractor.returnCheckPending } returns isReturnCheckPending
        coEvery { inAppInteractor.awaitReturnChecked() } coAnswers { returnChecked.await() }
        val recordedSessions = ConcurrentLinkedQueue<SessionEpoch>()
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } answers {
            recordedSessions.add(SessionEpoch(arg(5)))
            true
        }
        startAndAwaitPageLoad()
        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))
        await { states.lastOrNull() == EmbeddedBlockState.Ready }

        holder.withholdShow(true)
        isReturnCheckPending.value = false
        returnChecked.complete(Unit)
        letTheShowRecordingSettle()

        verify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) }

        holder.withholdShow(false)

        await { recordedSessions.isNotEmpty() }
        verify(exactly = 1) {
            inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), any(), any(), SessionEpoch(0L))
        }
    }

    @Test
    fun `a session refresh sent again for a session the page has not counted waits for the render that follows it`() {
        renderAndCountTheFirstShow()
        holder.refreshForSession(mapOf("items" to "[]"), sessionEpoch = SessionEpoch(1L), selectionTime = Milliseconds(0L)) {}
        answerThePush()
        holder.withholdShow(true)
        postFromPage(request(action = "contentRendered", payload = """{"count":2}""", id = "late-render"))
        await { lastOutgoingMessage()?.get("id")?.asString == "late-render" }

        holder.refreshForSession(mapOf("items" to "[]"), sessionEpoch = SessionEpoch(1L), selectionTime = Milliseconds(0L)) {}
        answerThePush()
        holder.withholdShow(false)
        letTheShowRecordingSettle()

        verify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), SessionEpoch(1L)) }

        postFromPage(request(action = "contentRendered", payload = """{"count":2}""", id = "after-resend"))

        verify(exactly = 1, timeout = 5_000L) {
            inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), any(), any(), SessionEpoch(1L))
        }
    }

    @Test
    fun `a session refresh sent again for the same session keeps the selection time and the time on screen of the first refresh`() {
        renderAndCountTheFirstShow()
        elapsed = 3_600_000L
        holder.refreshForSession(mapOf("items" to "[]"), sessionEpoch = SessionEpoch(1L), selectionTime = Milliseconds(400L)) {}
        answerThePush()
        val firstPush = lastOutgoingMessage()?.get("id")
        elapsed = 3_600_250L

        holder.refreshForSession(mapOf("items" to "[]"), sessionEpoch = SessionEpoch(1L), selectionTime = Milliseconds(50L)) {}
        await { lastOutgoingMessage()?.get("id") != firstPush }
        answerThePush()
        elapsed = 3_600_350L
        postFromPage(request(action = "contentRendered", payload = """{"count":2}""", id = "after-resend"))

        verify(exactly = 1, timeout = 5_000L) {
            inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), Milliseconds(750L), any(), SessionEpoch(1L))
        }
    }

    @Test
    fun `a page refreshed for a new session tells the time it counted for that session so far and nothing for another session`() {
        renderAndCountTheFirstShow()
        elapsed = 1_000L
        holder.refreshForSession(mapOf("items" to "[]"), sessionEpoch = SessionEpoch(1L), selectionTime = Milliseconds(500L)) {}
        elapsed = 8_000L

        assertEquals(Milliseconds(7_500L), holder.timeToDisplaySoFar(SessionEpoch(1L)))
        assertNull(holder.timeToDisplaySoFar(SessionEpoch(0L)))
        assertNull(holder.timeToDisplaySoFar(SessionEpoch(2L)))
    }

    @Test
    fun `a page tells no time so far for a session whose show it already counted`() {
        renderAndCountTheFirstShow()

        assertNull(holder.timeToDisplaySoFar(SessionEpoch(0L)))
    }

    private fun pushDataTheShownPageRendersNothingFor() {
        holder.updateParams(mapOf("items" to "[]")) {}
        answerThePush()
        postFromPage(request(action = "contentRendered", payload = """{"count":0}""", id = "emptied"))
        await { holder.rendersNothing }
    }

    @Test
    fun `a shown page that renders nothing after a new session's data keeps the block shown, answers the page and sends no show`() {
        renderAndCountTheFirstShow()

        holder.refreshForSession(mapOf("items" to "[]"), sessionEpoch = SessionEpoch(1L), selectionTime = Milliseconds(0L)) {}
        answerThePush()
        postFromPage(request(action = "contentRendered", payload = """{"count":0}""", id = "after-refresh"))
        await { lastOutgoingMessage()?.get("id")?.asString == "after-refresh" }
        letTheShowRecordingSettle()

        assertEquals(EmbeddedBlockState.Ready, states.last())
        assertFalse(EmbeddedBlockState.Empty in states)
        assertTrue(holder.contentView != null)
        assertTrue(holder.rendersNothing)
        assertEquals("response", lastOutgoingMessage()!!.get("type").asString)
        verify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), SessionEpoch(1L)) }
    }

    @Test
    fun `a shown page that renders nothing tells its block`() {
        var emptiedReports = 0
        holder.onRendersNothing = { emptiedReports++ }
        renderAndCountTheFirstShow()

        pushDataTheShownPageRendersNothingFor()

        assertEquals(1, emptiedReports)
    }

    @Test
    fun `a page that renders content again after rendering nothing lets the new session's show go`() {
        renderAndCountTheFirstShow()
        holder.refreshForSession(mapOf("items" to "[]"), sessionEpoch = SessionEpoch(1L), selectionTime = Milliseconds(0L)) {}
        answerThePush()
        postFromPage(request(action = "contentRendered", payload = """{"count":0}""", id = "emptied"))
        await { holder.rendersNothing }

        postFromPage(request(action = "contentRendered", payload = """{"count":2}""", id = "refilled"))

        verify(exactly = 1, timeout = 5_000L) {
            inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), any(), any(), SessionEpoch(1L))
        }
        assertFalse(holder.rendersNothing)
    }

    @Test
    fun `a show the place lets go while the page renders nothing waits until the page renders content`() {
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } returns true
        startAndAwaitPageLoad()
        holder.withholdShow(true)
        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))
        await { states.lastOrNull() == EmbeddedBlockState.Ready }
        pushDataTheShownPageRendersNothingFor()

        holder.withholdShow(false)
        letTheShowRecordingSettle()

        verify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) }

        postFromPage(request(action = "contentRendered", payload = """{"count":2}""", id = "refilled"))

        verify(exactly = 1, timeout = 5_000L) {
            inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), any(), any(), SessionEpoch(0L))
        }
    }

    @Test
    fun `a show waiting for the session check of the return does not go once the page renders nothing meanwhile, and goes once it renders content`() {
        val returnChecked = CompletableDeferred<Unit>()
        val isReturnCheckPending = MutableStateFlow(true)
        every { inAppInteractor.returnCheckPending } returns isReturnCheckPending
        coEvery { inAppInteractor.awaitReturnChecked() } coAnswers { returnChecked.await() }
        val recordedSessions = ConcurrentLinkedQueue<SessionEpoch>()
        every { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) } answers {
            recordedSessions.add(SessionEpoch(arg(5)))
            true
        }
        startAndAwaitPageLoad()
        postFromPage(request(action = "contentRendered", payload = """{"count":3}"""))
        await { states.lastOrNull() == EmbeddedBlockState.Ready }
        pushDataTheShownPageRendersNothingFor()

        isReturnCheckPending.value = false
        returnChecked.complete(Unit)
        letTheShowRecordingSettle()

        verify(exactly = 0) { inAppInteractor.recordBlockShow(any(), any(), any(), any(), any(), any()) }

        postFromPage(request(action = "contentRendered", payload = """{"count":2}""", id = "refilled"))

        await { recordedSessions.isNotEmpty() }
        verify(exactly = 1) {
            inAppInteractor.recordBlockShow(PlaceKey.of("main-screen-top"), "embedded-id", any(), any(), any(), SessionEpoch(0L))
        }
    }
}
