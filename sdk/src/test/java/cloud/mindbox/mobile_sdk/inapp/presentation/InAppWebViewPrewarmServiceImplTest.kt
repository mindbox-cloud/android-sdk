package cloud.mindbox.mobile_sdk.inapp.presentation

import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadBlankDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.inapp.data.managers.MobileConfigSerializationManagerImpl
import cloud.mindbox.mobile_sdk.inapp.data.validators.WebViewLayerValidator
import cloud.mindbox.mobile_sdk.inapp.domain.models.Form
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppConfig
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.webview.InAppWebViewPrewarmEngine
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.Constants
import cloud.mindbox.mobile_sdk.utils.RuntimeTypeAdapterFactory
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class InAppWebViewPrewarmServiceImplTest {

    private val configuration = Configuration(
        previousInstallationId = "",
        previousDeviceUUID = "",
        endpointId = "Test.Endpoint",
        domain = "api.mindbox.ru",
        packageName = "test.package",
        versionName = "1.0",
        versionCode = "1",
        subscribeCustomerIfCreated = false,
        shouldCreateCustomer = false
    )

    private val webViewInApp = InAppStub.getInApp().copy(
        form = Form(
            variants = listOf(
                InAppType.WebView(
                    inAppId = "id",
                    type = "webview",
                    layers = listOf(
                        Layer.WebViewLayer(
                            baseUrl = "https://inapp.local/popup",
                            contentUrl = "https://mobile-static.mindbox.ru/content/index.html",
                            type = "webview",
                            params = emptyMap()
                        )
                    )
                )
            )
        )
    )

    private lateinit var engine: InAppWebViewPrewarmEngine
    private lateinit var gatewayManager: GatewayManager
    private lateinit var service: InAppWebViewPrewarmServiceImpl

    @Before
    fun setUp() {
        mockkObject(Mindbox)
        every { Mindbox.mindboxScope } returns CoroutineScope(UnconfinedTestDispatcher())
        mockkObject(DbManager)
        every { DbManager.listenConfigurations() } returns flowOf(configuration)
        mockkObject(MindboxPreferences)
        every { MindboxPreferences.deviceUuid } returns "test-device-uuid"
        engine = mockk(relaxed = true)
        gatewayManager = mockk(relaxed = true)
        coEvery { gatewayManager.fetchWebViewContent(any()) } returns "<html></html>"
        service = InAppWebViewPrewarmServiceImpl(
            engine = engine,
            mobileConfigSerializationManager = MobileConfigSerializationManagerImpl(gson = configGson()),
            gatewayManager = lazyOf(gatewayManager),
            inAppValidator = mockk(relaxed = true) {
                every { validateInAppVersion(any()) } returns true
            },
            webViewLayerValidator = WebViewLayerValidator(),
            learnedHostsStore = mockk(relaxed = true) {
                every { hosts(any()) } returns listOf("learned-cdn.mindbox.ru")
            }
        )
    }

    @After
    fun tearDown() {
        unmockkObject(Mindbox)
        unmockkObject(DbManager)
        unmockkObject(MindboxPreferences)
    }

    private fun configWith(vararg inApps: cloud.mindbox.mobile_sdk.inapp.domain.models.InApp) = InAppConfig(
        inApps = inApps.toList(),
        monitoring = emptyList(),
        operations = emptyMap(),
        abtests = emptyList()
    )

    @Test
    fun `prewarmResources loads preconnect and content page once`() {
        service.prewarmResources(configWith(webViewInApp))
        service.prewarmResources(configWith(webViewInApp))

        verify(exactly = 1) {
            engine.loadPreconnectPage(
                html = match { html ->
                    html.contains("https://mobile-static.mindbox.ru") &&
                        html.contains("https://api.mindbox.ru") &&
                        html.contains("https://learned-cdn.mindbox.ru")
                },
                baseUrl = "https://inapp.local/popup",
                userAgentSuffix = any()
            )
        }
        coVerify(exactly = 1) { gatewayManager.fetchWebViewContent("https://mobile-static.mindbox.ru/content/index.html") }
        verify(exactly = 1) {
            engine.loadContentPage(
                html = "<html></html>",
                // Official prewarm contract: the content page's document URL carries the params.
                baseUrl = "https://inapp.local/popup?prewarm=1&endpointId=Test.Endpoint&deviceUuid=test-device-uuid",
                userAgentSuffix = any()
            )
        }
    }

    @Test
    fun `prewarm webview is released by network idle well before the hard cap`() = runTest {
        every { Mindbox.mindboxScope } returns CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        // Page reports a stable resource list whose last download finished long ago.
        every { engine.evaluateJavaScript(any(), any()) } answers {
            secondArg<(String?) -> Unit>().invoke("\"6:5000\"")
        }

        service.prewarmResources(configWith(webViewInApp))
        verify(exactly = 0) { engine.release() }

        // Two stable polls after the baseline one -> idle at the third second, not at 30s.
        advanceTimeBy(3_100)
        verify(exactly = 1) { engine.release() }
        verify(exactly = 0) { engine.abort() }
    }

    @Test
    fun `garbage probes are charged the probe timeout and drain the cap budget early`() = runTest {
        every { Mindbox.mindboxScope } returns CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        every { engine.evaluateJavaScript(any(), any()) } answers {
            secondArg<(String?) -> Unit>().invoke("\"\"")
        }

        service.prewarmResources(configWith(webViewInApp))

        // Each 1s poll yields a garbage (null) probe charged the 2s probe timeout on top of
        // its own second, so the 30-unit budget drains after 10 polls — the cap is a
        // wall-clock bound even when the page never answers usefully.
        advanceTimeBy(9_500)
        verify(exactly = 0) { engine.release() }
        advanceTimeBy(1_000)
        verify(exactly = 1) { engine.release() }
    }

    @Test
    fun `a real show during the settle poll stops the poller at the next tick`() = runTest {
        every { Mindbox.mindboxScope } returns CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        // The page keeps downloading (entry count grows every poll), so the poll never
        // goes idle by itself.
        var count = 0
        every { engine.evaluateJavaScript(any(), any()) } answers {
            count++
            secondArg<(String?) -> Unit>().invoke("\"$count:100\"")
        }

        service.prewarmResources(configWith(webViewInApp))
        advanceTimeBy(2_500)
        service.onRealShowWillStart()
        advanceTimeBy(60_000)

        // The poller stops (job cancelled; the per-tick hasAborted guard is the backstop):
        // the terminal abort stays the only teardown, no second release lands mid-show.
        verify(exactly = 1) { engine.abort() }
        verify(exactly = 0) { engine.release() }
    }

    @Test
    fun `terminal preempt during the content fetch prevents both the content load and the settle poll`() = runTest {
        every { Mindbox.mindboxScope } returns CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        // The real show starts while the prewarm is suspended in the content fetch.
        coEvery { gatewayManager.fetchWebViewContent(any()) } coAnswers {
            service.onRealShowWillStart()
            "<html></html>"
        }

        service.prewarmResources(configWith(webViewInApp))
        advanceTimeBy(31_000)

        verify(exactly = 0) { engine.loadContentPage(any(), any(), any()) }
        // Only the terminal abort released the engine; no zombie poll produced a second one.
        verify(exactly = 1) { engine.abort() }
        verify(exactly = 0) { engine.release() }
    }

    @Test
    fun `a no-layers config arriving mid-fetch stops the content load from resurrecting a webview`() = runTest {
        every { Mindbox.mindboxScope } returns CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { gatewayManager.fetchWebViewContent(any()) } coAnswers {
            service.prewarmResources(configWith(InAppStub.getInApp()))
            "<html></html>"
        }

        service.prewarmResources(configWith(webViewInApp))
        advanceTimeBy(31_000)

        verify(exactly = 0) { engine.loadContentPage(any(), any(), any()) }
        // The resumed prewarm must RELEASE, not bare-return: its preconnect load already
        // created a WebView, and this path schedules no settle poll to free it later.
        // (First release comes from the no-layers branch itself, second from the resume.)
        verify(exactly = 2) { engine.release() }
    }

    @Test
    fun `a no-layers config arriving before the preconnect load keeps the webview from being created`() = runTest {
        every { Mindbox.mindboxScope } returns CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        // The fresh no-layers config lands while the prewarm is suspended reading the
        // saved configuration — before it has touched the engine at all.
        every { DbManager.listenConfigurations() } answers {
            service.prewarmResources(configWith(InAppStub.getInApp()))
            flowOf(configuration)
        }

        service.prewarmResources(configWith(webViewInApp))
        advanceTimeBy(31_000)

        verify(exactly = 0) { engine.loadPreconnectPage(any(), any(), any()) }
        verify(exactly = 0) { engine.loadContentPage(any(), any(), any()) }
    }

    @Test
    fun `an attempt without a saved configuration does not consume the one-shot`() {
        every { DbManager.listenConfigurations() } returnsMany listOf(
            kotlinx.coroutines.flow.emptyFlow(),
            flowOf(configuration)
        )

        // First attempt: configuration read fails -> skipped, one-shot must survive.
        service.prewarmResources(configWith(webViewInApp))
        verify(exactly = 0) { engine.loadContentPage(any(), any(), any()) }

        // Second attempt with a readable configuration warms normally.
        service.prewarmResources(configWith(webViewInApp))
        verify(exactly = 1) {
            engine.loadContentPage(
                any(),
                "https://inapp.local/popup?prewarm=1&endpointId=Test.Endpoint&deviceUuid=test-device-uuid",
                any()
            )
        }
    }

    @Test
    fun `prewarmResources releases warm webview when config has no webview inapps`() {
        service.prewarmResources(configWith(InAppStub.getInApp()))

        verify(exactly = 1) { engine.release() }
        verify(exactly = 0) { engine.loadPreconnectPage(any(), any(), any()) }
        verify(exactly = 0) { engine.loadContentPage(any(), any(), any()) }
    }

    @Test
    fun `real show preempts prewarm terminally and blocks later attempts`() {
        service.onRealShowWillStart()
        service.prewarmResources(configWith(webViewInApp))

        verify(exactly = 1) { engine.abort() }
        verify(exactly = 0) { engine.loadPreconnectPage(any(), any(), any()) }
        verify(exactly = 0) { engine.loadContentPage(any(), any(), any()) }
    }

    @Test
    fun `prewarmOnInit warms from cached config without validation pipeline`() {
        every { MindboxPreferences.inAppConfig } returns cachedConfigJson()

        service.prewarmOnInit()

        verify(exactly = 1) { engine.loadPreconnectPage(any(), "https://inapp.local/popup", any()) }
        verify(exactly = 1) {
            engine.loadContentPage(
                any(),
                "https://inapp.local/popup?prewarm=1&endpointId=Test.Endpoint&deviceUuid=test-device-uuid",
                any()
            )
        }
    }

    @Test
    fun `prewarmOnInit does nothing without cached config`() {
        every { MindboxPreferences.inAppConfig } returns ""

        service.prewarmOnInit()

        verify(exactly = 0) { engine.loadPreconnectPage(any(), any(), any()) }
    }

    /** Same `${'$'}type` adapters the production gson registers for the form payload path. */
    private fun configGson(): Gson = GsonBuilder()
        .registerTypeAdapterFactory(
            RuntimeTypeAdapterFactory
                .of(PayloadBlankDto::class.java, Constants.TYPE_JSON_NAME, true)
                .registerSubtype(PayloadBlankDto.ModalWindowBlankDto::class.java, PayloadDto.ModalWindowDto.MODAL_JSON_NAME)
                .registerSubtype(PayloadBlankDto.SnackBarBlankDto::class.java, PayloadDto.SnackbarDto.SNACKBAR_JSON_NAME)
        )
        .registerTypeAdapterFactory(
            RuntimeTypeAdapterFactory
                .of(BackgroundDto.LayerDto::class.java, Constants.TYPE_JSON_NAME, true)
                .registerSubtype(
                    BackgroundDto.LayerDto.ImageLayerDto::class.java,
                    BackgroundDto.LayerDto.ImageLayerDto.IMAGE_TYPE_JSON_NAME
                )
                .registerSubtype(
                    BackgroundDto.LayerDto.WebViewLayerDto::class.java,
                    BackgroundDto.LayerDto.WebViewLayerDto.WEBVIEW_TYPE_JSON_NAME
                )
        )
        .create()

    private fun cachedConfigJson(): String = """
        {
          "inapps": [
            {
              "id": "cached-webview",
              "sdkVersion": { "min": 1, "max": null },
              "targeting": { "${'$'}type": "true" },
              "form": {
                "variants": [
                  {
                    "${'$'}type": "modal",
                    "content": {
                      "background": {
                        "layers": [
                          {
                            "${'$'}type": "webview",
                            "baseUrl": "https://inapp.local/popup",
                            "contentUrl": "https://mobile-static.mindbox.ru/content/index.html",
                            "params": { "formId": "159510" }
                          }
                        ]
                      },
                      "elements": []
                    }
                  }
                ]
              }
            }
          ]
        }
        """.trimIndent()
}
