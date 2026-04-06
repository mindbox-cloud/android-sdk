package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.logger.mindboxLogW

internal class WebViewLayerValidator : Validator<BackgroundDto.LayerDto.WebViewLayerDto?> {

    override fun isValid(item: BackgroundDto.LayerDto.WebViewLayerDto?): Boolean {
        if (item == null) {
            mindboxLogW("InApp is invalid. WebView layer is null")
            return false
        }
        if (item.type != BackgroundDto.LayerDto.WebViewLayerDto.WEBVIEW_TYPE_JSON_NAME) {
            mindboxLogW(
                "InApp is invalid. WebView layer is expected to have type = ${BackgroundDto.LayerDto.WebViewLayerDto.WEBVIEW_TYPE_JSON_NAME}. " +
                    "Actual type = ${item.type}"
            )
            return false
        }
        if (item.baseUrl.isNullOrBlank()) {
            mindboxLogW(
                "InApp is invalid. WebView layer is expected to have non-blank baseUrl. " +
                    "Actual baseUrl = ${item.baseUrl}"
            )
            return false
        }
        if (item.contentUrl.isNullOrBlank()) {
            mindboxLogW(
                "InApp is invalid. WebView layer is expected to have non-blank contentUrl. " +
                    "Actual contentUrl = ${item.contentUrl}"
            )
            return false
        }
        return true
    }
}
