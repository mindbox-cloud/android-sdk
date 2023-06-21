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
    val variants: List<Payload>,
)

internal sealed class Payload {

    abstract fun mapToInAppType(id: String): InAppType
    data class SimpleImage(
        val type: String,
        val imageUrl: String,
        val redirectUrl: String,
        val intentPayload: String,
    ) : Payload() {
        override fun mapToInAppType(id: String): InAppType {
            return InAppType.SimpleImage(
                inAppId = id,
                imageUrl = imageUrl,
                redirectUrl = redirectUrl,
                intentData = intentPayload
            )
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
