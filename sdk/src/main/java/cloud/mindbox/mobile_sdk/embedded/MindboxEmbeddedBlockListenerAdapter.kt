package cloud.mindbox.mobile_sdk.embedded

/**
 * A [MindboxEmbeddedBlockListener] with both callbacks already implemented as no-ops — extend it
 * and override only the ones you need.
 *
 * **For Java hosts.** Kotlin classes can implement [MindboxEmbeddedBlockListener] directly and
 * still override one callback out of two; Java sees the interface methods as abstract and would
 * have to implement both, so this class exists to spare it the empty method.
 *
 * ```java
 * blockView.setListener(new MindboxEmbeddedBlockListenerAdapter() {
 *     @Override
 *     public void onLoad(MindboxEmbeddedBlockView view) {
 *         // the block is shown
 *     }
 * });
 * ```
 */
public abstract class MindboxEmbeddedBlockListenerAdapter : MindboxEmbeddedBlockListener {

    override fun onLoad(view: MindboxEmbeddedBlockView) {}

    override fun onFail(view: MindboxEmbeddedBlockView) {}
}
