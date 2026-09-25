package cloud.mindbox.mobile_sdk.embedded

/**
 * A [MindboxEmbeddedBlockListener] with every callback already implemented as a no-op — extend it
 * and override only the ones you need.
 *
 * **For Java hosts.** Kotlin classes can implement [MindboxEmbeddedBlockListener] directly and
 * still override one callback out of three; Java sees the interface methods as abstract and would
 * have to implement all of them, so this class exists to spare it the empty methods.
 *
 * ```java
 * blockView.setListener(new MindboxEmbeddedBlockListenerAdapter() {
 *     @Override
 *     public void onFail(MindboxEmbeddedBlockView view, MindboxEmbeddedBlockFailReason reason) {
 *         Log.w("Stories", "the block failed: " + reason);
 *     }
 * });
 * ```
 */
public abstract class MindboxEmbeddedBlockListenerAdapter : MindboxEmbeddedBlockListener {

    override fun onLoad(view: MindboxEmbeddedBlockView) {}

    override fun onEmpty(view: MindboxEmbeddedBlockView) {}

    override fun onFail(view: MindboxEmbeddedBlockView, reason: MindboxEmbeddedBlockFailReason) {}
}
