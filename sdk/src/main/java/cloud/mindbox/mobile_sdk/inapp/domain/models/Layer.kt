package cloud.mindbox.mobile_sdk.inapp.domain.models

internal sealed class Layer {
    internal data class ImageLayer(
        val action: Action,
        val source: Source
    ) : Layer() {
        internal sealed class Action {
            internal data class RedirectUrlAction(
                val url: String,
                val payload: String
            ) : Action()

            internal data class PushPermissionAction(
                val payload: String
            ) : Action()
        }

        internal sealed class Source {
            internal data class UrlSource(
                val url: String
            ) : Source()
        }
    }

    internal data class WebViewLayer(
        val baseUrl: String?,
        val contentUrl: String?,
        val additionalScripts: String?,
        val actionUrl: String? = null,
    ) : Layer()
}
