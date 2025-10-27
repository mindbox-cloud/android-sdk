package cloud.mindbox.mobile_sdk.inapp.presentation

import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class InAppMessageViewDisplayerImplTest {

    private lateinit var displayer: InAppMessageViewDisplayerImpl

    @Before
    fun setUp() {
        mockkObject(MindboxDI)
        every { MindboxDI.appModule } returns mockk {
            every { gson } returns Gson()
        }
        displayer = InAppMessageViewDisplayerImpl(mockk())
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getWebViewFromPayload returns WebView for valid redirectUrl payload`() {
        val payload = """
            {"${'$'}type":"webview","baseUrl":"https://base","contentUrl":"/content","params":{"a":"b"}}
        """.trimIndent()
        val imageLayer = Layer.ImageLayer(
            action = Layer.ImageLayer.Action.RedirectUrlAction(url = "https://example", payload = payload),
            source = Layer.ImageLayer.Source.UrlSource(url = "https://img")
        )
        val inApp = InAppType.Snackbar(
            inAppId = "inapp-1",
            type = PayloadDto.SnackbarDto.SNACKBAR_JSON_NAME,
            layers = listOf(imageLayer),
            elements = emptyList(),
            position = InAppType.Snackbar.Position(
                gravity = InAppType.Snackbar.Position.Gravity(
                    horizontal = InAppType.Snackbar.Position.Gravity.HorizontalGravity.CENTER,
                    vertical = InAppType.Snackbar.Position.Gravity.VerticalGravity.TOP
                ),
                margin = InAppType.Snackbar.Position.Margin(
                    kind = InAppType.Snackbar.Position.Margin.MarginKind.DP,
                    top = 0,
                    left = 0,
                    right = 0,
                    bottom = 0
                )
            )
        )
        val expected = InAppType.WebView(
            inAppId = "inapp-1",
            type = PayloadDto.WebViewDto.WEBVIEW_JSON_NAME,
            layers = listOf(
                Layer.WebViewLayer(
                    baseUrl = "https://base",
                    contentUrl = "/content",
                    type = "webview",
                    params = mapOf("a" to "b")
                )
            )
        )
        val actual = requireNotNull(displayer.getWebViewFromPayload(inApp, inApp.inAppId))
        assertEquals(expected, actual)
    }

    @Test
    fun `getWebViewFromPayload returns WebView for valid pushPermission payload`() {
        val payload = """
            {"${'$'}type":"webview","baseUrl":"https://b","contentUrl":"/c"}
        """.trimIndent()
        val imageLayer = Layer.ImageLayer(
            action = Layer.ImageLayer.Action.PushPermissionAction(payload = payload),
            source = Layer.ImageLayer.Source.UrlSource(url = "https://img")
        )
        val inApp = InAppType.ModalWindow(
            inAppId = "inapp-2",
            type = PayloadDto.ModalWindowDto.MODAL_JSON_NAME,
            layers = listOf(imageLayer),
            elements = emptyList()
        )
        val expected = InAppType.WebView(
            inAppId = "inapp-2",
            type = PayloadDto.WebViewDto.WEBVIEW_JSON_NAME,
            layers = listOf(
                Layer.WebViewLayer(
                    baseUrl = "https://b",
                    contentUrl = "/c",
                    type = "webview",
                    params = emptyMap()
                )
            )
        )
        val actual = requireNotNull(displayer.getWebViewFromPayload(inApp, inApp.inAppId))
        assertEquals(expected, actual)
    }

    @Test
    fun `getWebViewFromPayload returns null for empty json object`() {
        val payload = "{}"
        val imageLayer = Layer.ImageLayer(
            action = Layer.ImageLayer.Action.RedirectUrlAction(url = "https://example", payload = payload),
            source = Layer.ImageLayer.Source.UrlSource(url = "https://img")
        )
        val inApp = InAppType.Snackbar(
            inAppId = "inapp-3",
            type = PayloadDto.SnackbarDto.SNACKBAR_JSON_NAME,
            layers = listOf(imageLayer),
            elements = emptyList(),
            position = InAppType.Snackbar.Position(
                gravity = InAppType.Snackbar.Position.Gravity(
                    horizontal = InAppType.Snackbar.Position.Gravity.HorizontalGravity.CENTER,
                    vertical = InAppType.Snackbar.Position.Gravity.VerticalGravity.TOP
                ),
                margin = InAppType.Snackbar.Position.Margin(
                    kind = InAppType.Snackbar.Position.Margin.MarginKind.DP,
                    top = 0,
                    left = 0,
                    right = 0,
                    bottom = 0
                )
            )
        )
        val actual = displayer.getWebViewFromPayload(inApp, inApp.inAppId)
        assertNull(actual)
    }

    @Test
    fun `getWebViewFromPayload returns null for missing fields`() {
        val payload = """
            {"${'$'}type":"webview","baseUrl":"https://base"}
        """.trimIndent()
        val imageLayer = Layer.ImageLayer(
            action = Layer.ImageLayer.Action.PushPermissionAction(payload = payload),
            source = Layer.ImageLayer.Source.UrlSource(url = "https://img")
        )
        val inApp = InAppType.ModalWindow(
            inAppId = "inapp-4",
            type = PayloadDto.ModalWindowDto.MODAL_JSON_NAME,
            layers = listOf(imageLayer),
            elements = emptyList()
        )
        val actual = displayer.getWebViewFromPayload(inApp, inApp.inAppId)
        assertNull(actual)
    }

    @Test
    fun `getWebViewFromPayload returns null for invalid json`() {
        val payload = "not a json"
        val imageLayer = Layer.ImageLayer(
            action = Layer.ImageLayer.Action.RedirectUrlAction(url = "https://example", payload = payload),
            source = Layer.ImageLayer.Source.UrlSource(url = "https://img")
        )
        val inApp = InAppType.Snackbar(
            inAppId = "inapp-5",
            type = PayloadDto.SnackbarDto.SNACKBAR_JSON_NAME,
            layers = listOf(imageLayer),
            elements = emptyList(),
            position = InAppType.Snackbar.Position(
                gravity = InAppType.Snackbar.Position.Gravity(
                    horizontal = InAppType.Snackbar.Position.Gravity.HorizontalGravity.CENTER,
                    vertical = InAppType.Snackbar.Position.Gravity.VerticalGravity.TOP
                ),
                margin = InAppType.Snackbar.Position.Margin(
                    kind = InAppType.Snackbar.Position.Margin.MarginKind.DP,
                    top = 0,
                    left = 0,
                    right = 0,
                    bottom = 0
                )
            )
        )
        val actual = displayer.getWebViewFromPayload(inApp, inApp.inAppId)
        assertNull(actual)
    }
}
