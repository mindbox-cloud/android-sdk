package cloud.mindbox.mobile_sdk.models.operation.response

import com.google.gson.annotations.SerializedName

/**
 * In-app types
 **/
internal sealed class PayloadDto {
    data class ModalWindowDto(
        @SerializedName("content")
        val content: ContentDto?,
        @SerializedName("${"$"}type")
        val type: String?
    ) : PayloadDto() {

        internal companion object {
            const val MODAL_JSON_NAME = "modal"
        }

        internal data class ContentDto(
            @SerializedName("background")
            val background: BackgroundDto?,
            @SerializedName("elements")
            var elements: List<ElementDto?>?
        ) {
           internal data class BackgroundDto(
                @SerializedName("layers")
                var layers: List<LayerDto?>?
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

            internal sealed class ElementDto {

                internal abstract fun default(): ElementDto

                internal data class CloseButtonElementDto(
                    @SerializedName("color")
                    val color: String?,
                    @SerializedName("lineWidth")
                    val lineWidth: Any?,
                    @SerializedName("position")
                    val position: PositionDto?,
                    @SerializedName("size")
                    val size: SizeDto?,
                    @SerializedName("${"$"}type")
                    val type: String?
                ) : ElementDto() {


                    override fun default(): ElementDto {
                        return CloseButtonElementDto(
                            color = "#000000",
                            lineWidth = 4.0,
                            position = PositionDto.default(),
                            size = SizeDto.default(),
                            type = "closeButton"

                        )
                    }

                    internal companion object {
                        const val CLOSE_BUTTON_ELEMENT_JSON_NAME = "closeButton"
                    }

                    internal data class PositionDto(
                        @SerializedName("margin")
                        val margin: MarginDto?
                    ) {
                        internal companion object {
                            internal fun default(): PositionDto {
                                return PositionDto(margin = MarginDto.default())
                            }
                        }


                        internal data class MarginDto(
                            @SerializedName("bottom")
                            val bottom: Double?,
                            @SerializedName("kind")
                            val kind: String?,
                            @SerializedName("left")
                            val left: Double?,
                            @SerializedName("right")
                            var right: Double?,
                            @SerializedName("top")
                            val top: Double?
                        ) {
                            internal companion object {
                                internal fun default(): MarginDto {
                                    return MarginDto(
                                        bottom = 0.02,
                                        kind = "proportion",
                                        left = 0.02,
                                        right = 0.02,
                                        top = 0.02
                                    )
                                }
                            }

                        }
                    }

                    internal data class SizeDto(
                        @SerializedName("height")
                        val height: Double?,
                        @SerializedName("kind")
                        val kind: String?,
                        @SerializedName("width")
                        val width: Double?
                    ) {
                        internal companion object {
                            internal fun default(): SizeDto {
                                return SizeDto(height = 24.0, kind = "dp", width = 24.0)
                            }
                        }

                    }


                }
            }
        }
    }
}