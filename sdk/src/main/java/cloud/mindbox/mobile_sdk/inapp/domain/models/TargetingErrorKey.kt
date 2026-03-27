package cloud.mindbox.mobile_sdk.inapp.domain.models

internal sealed interface TargetingErrorKey {
    data object CustomerSegmentation : TargetingErrorKey

    data object Geo : TargetingErrorKey

    data class ProductSegmentation(
        val product: Pair<String, String>,
    ) : TargetingErrorKey
}
