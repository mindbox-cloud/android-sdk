package cloud.mindbox.mobile_sdk.embedded

import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason

internal sealed class EmbeddedBlockState {

    data object Loading : EmbeddedBlockState()

    data object Ready : EmbeddedBlockState()

    data object Empty : EmbeddedBlockState()

    data class Failed(val reason: MindboxEmbeddedBlockFailReason) : EmbeddedBlockState() {
        constructor(code: FailureReason) : this(code.toEmbeddedBlockFailReason())
    }

    val nothingToShow: Boolean
        get() = this is Empty || this is Failed
}
