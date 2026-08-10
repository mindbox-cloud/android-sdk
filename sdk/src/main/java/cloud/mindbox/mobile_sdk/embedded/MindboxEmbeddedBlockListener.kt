package cloud.mindbox.mobile_sdk.embedded

/**
 * Reports the outcome of a [MindboxEmbeddedBlockView]: the block either shows content, or the
 * place stays without it.
 *
 * The listener only observes — the block applies its own show/hide behavior before the callback
 * and works the same with no listener at all. Register with
 * [MindboxEmbeddedBlockView.setListener]; both methods are optional, override only what you need.
 *
 * Callbacks arrive on the main thread, each outcome once. A listener registered after the block
 * already loaded or failed still gets the current outcome.
 */
public interface MindboxEmbeddedBlockListener {

    /**
     * The content loaded and is visible inside the block.
     *
     * @param view The block that loaded — tell several blocks apart by
     * [MindboxEmbeddedBlockView.placeSystemName].
     */
    public fun onLoad(view: MindboxEmbeddedBlockView) {}

    /**
     * The place stays without content — the load failed or timed out, or the config had nothing
     * to put here. An empty place is a normal outcome, not a breakage.
     *
     * The block already hid itself, unless [MindboxEmbeddedBlockView.setErrorView] is set — then
     * it keeps its place and shows that view. Nothing is required here. The block recovers on
     * the next attach or when a new session brings a fresh config.
     *
     * @param view The block left without content.
     */
    public fun onFail(view: MindboxEmbeddedBlockView) {}
}
