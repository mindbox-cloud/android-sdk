package cloud.mindbox.mobile_sdk.embedded

/**
 * What the block shows until the SDK answers, given at creation. The same values exist on every
 * platform.
 */
public enum class MindboxEmbeddedBlockLoadingStrategy {

    /**
     * The default. Hidden until the place has shown content once on this device, a placeholder
     * from then on. The memory is per place and survives a restart; it is dropped when the place
     * answers with nothing to show and kept on a failure.
     */
    AUTOMATIC,

    /** A placeholder until the answer: the SDK's shimmer or the host's own. */
    PLACEHOLDER,

    /**
     * Hidden until the content is shown: no placeholder, and no error view until then. The
     * outcome still arrives through the listener.
     */
    HIDDEN,
}
