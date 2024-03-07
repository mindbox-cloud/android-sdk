package cloud.mindbox.mobile_sdk.inapp.data.dto

import com.google.gson.annotations.SerializedName

internal data class BackgroundDto(
    @SerializedName("layers")
    val layers: List<LayerDto?>?
) {
    internal sealed class LayerDto {
        internal data class ImageLayerDto(
            @SerializedName("action")
            val action: ActionDto?,
            @SerializedName("source")
            val source: SourceDto?,
            @SerializedName("${"$"}type")
            val type: String?
        ) : LayerDto() {
            internal companion object {
                const val IMAGE_TYPE_JSON_NAME = "image"
            }

            internal sealed class ActionDto {

                internal data class RedirectUrlActionDto(
                    @SerializedName("intentPayload")
                    val intentPayload: String?,
                    @SerializedName("${"$"}type")
                    val type: String?,
                    @SerializedName("value")
                    val value: String?
                ) : ActionDto() {
                    internal companion object {
                        const val REDIRECT_URL_ACTION_TYPE_JSON_NAME = "redirectUrl"
                    }
                }

                internal data class PushPermissionActionDto(
                    @SerializedName("intentPayload")
                    val intentPayload: String?,
                    @SerializedName("${"$"}type")
                    val type: String?,
                ) : ActionDto() {
                    internal companion object {
                        const val PUSH_PERMISSION_TYPE_JSON_NAME = "pushPermission"
                    }
                }
            }

            internal sealed class SourceDto {
                internal data class UrlSourceDto(
                    @SerializedName("${"$"}type")
                    val type: String?,
                    @SerializedName("value")
                    val value: String?
                ) : SourceDto() {
                    internal companion object {
                        const val URL_SOURCE_JSON_NAME = "url"
                    }
                }
            }
        }
    }
}