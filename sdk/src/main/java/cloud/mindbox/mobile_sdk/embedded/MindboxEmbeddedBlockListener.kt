package cloud.mindbox.mobile_sdk.embedded

/**
 * Receives the outcome of a [MindboxEmbeddedBlockView]. Every method has an empty default, and
 * all of them are called on the main thread. The block applies its own behavior before the call.
 */
public interface MindboxEmbeddedBlockListener {

    /** The content is shown. */
    public fun onLoad(view: MindboxEmbeddedBlockView) {}

    /** The place has nothing to show; the block collapsed. */
    public fun onEmpty(view: MindboxEmbeddedBlockView) {}

    /**
     * The block could not get or show its content; the block collapsed or shows the error view.
     * [reason] is one of the [MindboxEmbeddedBlockFailReason] constants.
     */
    public fun onFail(view: MindboxEmbeddedBlockView, reason: MindboxEmbeddedBlockFailReason) {}
}
