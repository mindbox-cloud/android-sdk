package cloud.mindbox.mobile_sdk.embedded

/**
 * A [MindboxEmbeddedBlockListener] with empty methods, for Java hosts that override only some of
 * them.
 */
public abstract class MindboxEmbeddedBlockListenerAdapter : MindboxEmbeddedBlockListener {

    override fun onLoad(view: MindboxEmbeddedBlockView) {}

    override fun onEmpty(view: MindboxEmbeddedBlockView) {}

    override fun onFail(view: MindboxEmbeddedBlockView, reason: MindboxEmbeddedBlockFailReason) {}
}
