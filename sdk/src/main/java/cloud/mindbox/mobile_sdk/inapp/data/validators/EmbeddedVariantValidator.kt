package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.logger.mindboxLogW

internal class EmbeddedVariantValidator(
    private val webViewLayerValidator: WebViewLayerValidator,
) : Validator<PayloadDto.EmbeddedDto?> {

    override fun isValid(item: PayloadDto.EmbeddedDto?): Boolean {
        if (item == null) {
            mindboxLogW("InApp is invalid. Embedded variant is null")
            return false
        }
        if (item.type != PayloadDto.EmbeddedDto.EMBEDDED_JSON_NAME) {
            mindboxLogW(
                "InApp is invalid. Embedded variant is expected to have type = " +
                    "${PayloadDto.EmbeddedDto.EMBEDDED_JSON_NAME}. Actual type = ${item.type}"
            )
            return false
        }
        if (item.placeSystemName.isNullOrEmpty()) {
            mindboxLogW("InApp is invalid. Embedded variant has no placeSystemName")
            return false
        }
        val layers = item.content?.background?.layers.orEmpty()
        val webViewLayer = layers.filterIsInstance<BackgroundDto.LayerDto.WebViewLayerDto>().firstOrNull()
        if (webViewLayer == null) {
            mindboxLogW(
                "InApp is invalid. Embedded variant has no webview layer. " +
                    "Actual layer count = ${layers.size}"
            )
            return false
        }
        if (layers.size > 1) {
            mindboxLogW(
                "Embedded variant has ${layers.size} layers; the first webview layer is used, " +
                    "the rest are ignored"
            )
        }
        return webViewLayerValidator.isValid(webViewLayer)
    }
}
