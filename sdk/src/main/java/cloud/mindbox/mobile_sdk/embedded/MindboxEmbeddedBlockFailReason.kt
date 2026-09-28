package cloud.mindbox.mobile_sdk.embedded

import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason

/**
 * Why a [MindboxEmbeddedBlockView] failed — the argument of [MindboxEmbeddedBlockListener.onFail].
 *
 * A class with constants, not an enum or a sealed class: later SDK versions may add reasons, and
 * a `when` on it always needs an `else` branch, so a new reason breaks no host. Meant for the
 * host's logs and analytics — the block has already applied its own behavior, nothing has to be
 * decided from it.
 */
public class MindboxEmbeddedBlockFailReason private constructor(
    /** The stable name of the reason, the same on every platform. */
    public val value: String,
) {

    override fun equals(other: Any?): Boolean = other is MindboxEmbeddedBlockFailReason && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    public companion object {

        /**
         * The content is unavailable because of the environment: no network or no config, the SDK
         * gave no answer within its wait budget, or the block page did not load.
         */
        @JvmField
        public val NETWORK_ERROR: MindboxEmbeddedBlockFailReason = MindboxEmbeddedBlockFailReason("networkError")

        /**
         * An error on the Mindbox side: the page loaded but did not report its content or reported
         * something unusable, or the SDK failed inside.
         */
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
