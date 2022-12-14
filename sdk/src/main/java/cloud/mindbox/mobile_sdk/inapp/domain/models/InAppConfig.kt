package cloud.mindbox.mobile_sdk.inapp.domain.models

internal data class InAppConfig(
    val inApps: List<InApp>,
)

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
    data class SimpleImage(
        val type: String,
        val imageUrl: String,
        val redirectUrl: String,
        val intentPayload: String,
    ) : Payload()
}
