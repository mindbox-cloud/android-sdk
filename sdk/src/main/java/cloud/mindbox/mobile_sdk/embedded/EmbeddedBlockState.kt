package cloud.mindbox.mobile_sdk.embedded

internal sealed class EmbeddedBlockState {

    data object Loading : EmbeddedBlockState()

    data object Ready : EmbeddedBlockState()

    data object Empty : EmbeddedBlockState()

    data object Failed : EmbeddedBlockState()

    val nothingToShow: Boolean
        get() = this is Empty || this is Failed
}
