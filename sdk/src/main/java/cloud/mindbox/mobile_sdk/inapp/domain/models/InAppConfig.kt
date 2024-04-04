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
    val frequency: Frequency,
    val targeting: TreeTargeting,
    val form: Form,
)

internal data class Frequency(val delay: Delay) {
    internal sealed class Delay {
        object LifetimeDelay: Delay()
        data class TimeDelay(val time: Long): Delay()
    }
}

internal data class Form(
    val variants: List<InAppType>,
)

internal sealed class InAppType(open val inAppId: String) {

    internal data class Snackbar(
        override val inAppId: String,
        val type: String,
        val layers: List<Layer>,
        val elements: List<Element>,
        val position: Position
    ) : InAppType(inAppId) {
        internal data class Position(val gravity: Gravity,  val margin: Margin) {

            internal data class Margin(
                val kind: MarginKind,
                val top: Int,
                val left: Int,
                val right: Int,
                val bottom: Int
            ) {
                internal enum class MarginKind {
                    DP
                }
            }
            internal data class Gravity(
                val horizontal: HorizontalGravity,
                val vertical: VerticalGravity
            ) {
                internal enum class HorizontalGravity {
                    CENTER
                }
                internal enum class VerticalGravity{
                    TOP,
                    BOTTOM
                }
            }
        }
    }

    internal data class ModalWindow(
        override val inAppId: String,
        val type: String,
        val layers: List<Layer>,
        val elements: List<Element>
    ) : InAppType(inAppId)

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
