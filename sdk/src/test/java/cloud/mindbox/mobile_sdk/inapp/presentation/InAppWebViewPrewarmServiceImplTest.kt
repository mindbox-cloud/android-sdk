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
            gatewayManager = gatewayManager,
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
                baseUrl = "https://inapp.local/popup",
                endpointId = "Test.Endpoint",
                deviceUuid = "test-device-uuid",
                userAgentSuffix = any()
            )
        }
    }

    @Test
    fun `prewarmResources releases warm webview when config has no webview inapps`() {
        service.prewarmResources(configWith(InAppStub.getInApp()))

        verify(exactly = 1) { engine.release() }
        verify(exactly = 0) { engine.loadPreconnectPage(any(), any(), any()) }
        verify(exactly = 0) { engine.loadContentPage(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `real show preempts prewarm terminally and blocks later attempts`() {
        service.onRealShowWillStart()
        service.prewarmResources(configWith(webViewInApp))

        verify(exactly = 1) { engine.abort() }
        verify(exactly = 0) { engine.loadPreconnectPage(any(), any(), any()) }
        verify(exactly = 0) { engine.loadContentPage(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `prewarmOnInit warms from cached config without validation pipeline`() {
        every { MindboxPreferences.inAppConfig } returns cachedConfigJson()

        service.prewarmOnInit()

        verify(exactly = 1) { engine.loadPreconnectPage(any(), "https://inapp.local/popup", any()) }
        verify(exactly = 1) {
            engine.loadContentPage(any(), "https://inapp.local/popup", "Test.Endpoint", "test-device-uuid", any())
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
