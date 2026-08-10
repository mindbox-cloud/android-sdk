package cloud.mindbox.mobile_sdk.embedded

internal sealed class EmbeddedContentResolution {

    data class Content(val provider: EmbeddedContentProvider) : EmbeddedContentResolution()

    data object NothingToShow : EmbeddedContentResolution()

    data object NotReadyYet : EmbeddedContentResolution()
}
