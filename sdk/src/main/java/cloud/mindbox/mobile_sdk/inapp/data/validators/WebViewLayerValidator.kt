package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.logger.mindboxLogD

internal class WebViewLayerValidator : Validator<BackgroundDto.LayerDto.WebViewLayerDto?> {

    override fun isValid(item: BackgroundDto.LayerDto.WebViewLayerDto?): Boolean {
        val typeValid = item?.type == BackgroundDto.LayerDto.WebViewLayerDto.WEBVIEW_TYPE_JSON_NAME
        val baseUrlValid = !item?.baseUrl.isNullOrBlank()
        val contentUrlValid = !item?.contentUrl.isNullOrBlank()
        val result = typeValid && baseUrlValid && contentUrlValid
        if (!result) {
            mindboxLogD(
                "InApp is invalid. WebView layer is expected to have type = ${BackgroundDto.LayerDto.WebViewLayerDto.WEBVIEW_TYPE_JSON_NAME}, " +
                    "non-blank baseUrl and contentUrl. " +
                    "Actual webview layer type = ${item?.type}, baseUrl = ${item?.baseUrl}, contentUrl = ${item?.contentUrl}"
            )
        }
        return result
    }
}
