package cloud.mindbox.mobile_sdk.embedded

import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason

/**
 * Why a block failed, for logs and analytics. A class with constants, not an enum: later SDK
 * versions may add reasons, so a `when` on it needs an `else` branch.
 */
public class MindboxEmbeddedBlockFailReason private constructor(
    /** The name of the reason, the same on every platform. */
    public val value: String,
) {

    override fun equals(other: Any?): Boolean = other is MindboxEmbeddedBlockFailReason && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    public companion object {

        /** No network or no config, no answer within the timeout, or the page did not load. */
        @JvmField
        public val NETWORK_ERROR: MindboxEmbeddedBlockFailReason = MindboxEmbeddedBlockFailReason("networkError")

        /** The page did not report usable content, or the SDK failed inside. */
        @JvmField
        public val INTERNAL_ERROR: MindboxEmbeddedBlockFailReason = MindboxEmbeddedBlockFailReason("internalError")
    }
}

internal fun FailureReason.toEmbeddedBlockFailReason(): MindboxEmbeddedBlockFailReason = when (this) {
    FailureReason.WAIT_BUDGET_EXCEEDED,
    FailureReason.WEBVIEW_LOAD_FAILED,
    -> MindboxEmbeddedBlockFailReason.NETWORK_ERROR
    FailureReason.PRESENTATION_FAILED,
    FailureReason.UNKNOWN_ERROR,
    FailureReason.IMAGE_DOWNLOAD_FAILED,
    FailureReason.GEO_TARGETING_FAILED,
    FailureReason.CUSTOMER_SEGMENT_REQUEST_FAILED,
    FailureReason.PRODUCT_SEGMENT_REQUEST_FAILED,
    FailureReason.WEBVIEW_PRESENTATION_FAILED,
    -> MindboxEmbeddedBlockFailReason.INTERNAL_ERROR
}
