package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.monitoring.domain.models.LogRequest

internal data class InAppConfig(
    val inApps: List<InApp>,
    val monitoring: List<LogRequest>,
    val operations: Map<OperationName, OperationSystemName>,
    val abtests: List<ABTest>,
)

internal enum class OperationName(val operation: String) {
    VIEW_PRODUCT("viewProduct"),
    VIEW_CATEGORY("viewCategory"),
    SET_CART("setCart"),
}

@JvmInline
internal value class OperationSystemName(val systemName: String)

internal data class InApp(
    val id: String,
    val minVersion: Int?,
    val maxVersion: Int?,
    val targeting: TreeTargeting,
    val form: Form,
)

internal data class Form(
    val variants: List<InAppType>,
)

internal sealed class InAppType(open val inAppId: String) {
    internal data class ModalWindow(
        override val inAppId: String,
        val type: String,
        val layers: List<Layer>,
        val elements: List<Element>
    ) : InAppType(inAppId) {
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
                }

                internal sealed class Source {
                    internal data class UrlSource(
                        val url: String
                    ) : Source()
                }
            }
        }

        internal sealed class Element {
            internal data class CloseButton(
                val color: String,
                val lineWidth: Double,
                val size: Size,
                val position: Position
            ) : Element() {

                internal data class Position(
                    val top: Double,
                    val right: Double,
                    val left: Double,
                    val bottom: Double,
                    val kind: Kind
                ) {
                    internal enum class Kind {
                        PROPORTION
                    }
                }

                internal data class Size(val width: Double, val height: Double, val kind: Kind) {
                    internal enum class Kind {
                        DP
                    }
                }
            }
        }
    }
}

internal data class ABTest(
    val id: String,
    val minVersion: Int?,
    val maxVersion: Int?,
    val salt: String,
    val variants: List<Variant>,
) {
    internal data class Variant(
        val id: String,
        val type: String,
        val kind: VariantKind,
        val lower: Int,
        val upper: Int,
        val inapps: List<String>,
    ) {
        enum class VariantKind {
            ALL,
            CONCRETE,
        }
    }
}
