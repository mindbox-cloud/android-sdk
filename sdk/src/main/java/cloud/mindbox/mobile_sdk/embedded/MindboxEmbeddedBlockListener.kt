package cloud.mindbox.mobile_sdk.embedded

/**
 * Reports the outcome of a [MindboxEmbeddedBlockView]: the block either shows content, or the
 * place stays without it.
 *
 * The listener only observes — the block applies its own show/hide behavior before the callback
 * and works the same with no listener at all. Register with
 * [MindboxEmbeddedBlockView.setListener]; both methods are optional, override only what you need.
 * From Java, extend [MindboxEmbeddedBlockListenerAdapter] to get the same freedom.
 *
 * Callbacks arrive on the main thread, each outcome once. A listener registered after the block
 * already loaded or failed still gets the current outcome. Registering the same listener again
 * changes nothing — the outcome is not replayed to someone who already heard it.
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
     * The block already hid itself, unless it failed and [MindboxEmbeddedBlockView.setErrorView]
     * is set — then it keeps its place and shows that view. An empty place collapses either way.
     * Nothing is required here: the block retries by itself, and how depends on why the place
     * stayed empty.
     *
     * - The config had no placement for this place — resolved again every time the block comes
     *   back on screen, and on a new session.
     * - The page failed to load — reloaded on a new session. Coming back on screen replays the
     *   same outcome instead: the page is already there and it is broken.
     * - The page stayed silent past its timeout — given another attempt when the block comes back
     *   on screen. A new session reloads it too, but only after that attempt.
     *
     * @param view The block left without content.
     */
    public fun onFail(view: MindboxEmbeddedBlockView) {}
}
