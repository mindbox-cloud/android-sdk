package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class WebViewLayerValidatorTest {

    private val webViewLayerValidator = WebViewLayerValidator()

    @Test
    fun `isValid returns true for valid WebViewLayerDto`() {
        val webViewLayerDto = BackgroundDto.LayerDto.WebViewLayerDto(
            baseUrl = "https://inapp.local/popup",
            contentUrl = "https://inapp-dev.html",
            type = "webview",
            params = mapOf("formId" to "73379")
        )
        assertTrue(webViewLayerValidator.isValid(webViewLayerDto))
    }

    @Test
    fun `isValid returns true for valid WebViewLayerDto with empty params`() {
        val webViewLayerDto = BackgroundDto.LayerDto.WebViewLayerDto(
            baseUrl = "https://inapp.local/popup",
            contentUrl = "https://inapp-dev.html",
            type = "webview",
            params = null
        )
        assertTrue(webViewLayerValidator.isValid(webViewLayerDto))
    }

    @Test
    fun `isValid returns false when baseUrl is null`() {
        val webViewLayerDto = BackgroundDto.LayerDto.WebViewLayerDto(
            baseUrl = null,
            contentUrl = "https://inapp-dev.html",
            type = "webview",
            params = null
        )
        assertFalse(webViewLayerValidator.isValid(webViewLayerDto))
    }

    @Test
    fun `isValid returns false when baseUrl is blank`() {
        val webViewLayerDto = BackgroundDto.LayerDto.WebViewLayerDto(
            baseUrl = "   ",
            contentUrl = "https://inapp-dev.html",
            type = "webview",
            params = null
        )
        assertFalse(webViewLayerValidator.isValid(webViewLayerDto))
    }

    @Test
    fun `isValid returns false when contentUrl is null`() {
        val webViewLayerDto = BackgroundDto.LayerDto.WebViewLayerDto(
            baseUrl = "https://inapp.local/popup",
            contentUrl = null,
            type = "webview",
            params = null
        )
        assertFalse(webViewLayerValidator.isValid(webViewLayerDto))
    }

    @Test
    fun `isValid returns false when type is not webview`() {
        val webViewLayerDto = BackgroundDto.LayerDto.WebViewLayerDto(
            baseUrl = "https://inapp.local/popup",
            contentUrl = "https://inapp-dev.html",
            type = "image",
            params = null
        )
        assertFalse(webViewLayerValidator.isValid(webViewLayerDto))
    }

    @Test
    fun `isValid returns false when item is null`() {
        assertFalse(webViewLayerValidator.isValid(null))
    }
}
