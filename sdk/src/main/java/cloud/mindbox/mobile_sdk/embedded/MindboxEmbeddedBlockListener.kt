package cloud.mindbox.mobile_sdk.embedded

/**
 * Reports the outcome of a [MindboxEmbeddedBlockView]: the block shows content, the place has
 * nothing to show, or the block failed.
 *
 * The listener only observes — the block applies its own show/hide behavior before the callback
 * and works the same with no listener at all. Register with
 * [MindboxEmbeddedBlockView.setListener]; every method is optional, override only what you need.
 * From Java, extend [MindboxEmbeddedBlockListenerAdapter] to get the same freedom.
 *
 * Callbacks arrive on the main thread, each outcome once. A listener registered after the block
 * already settled still gets the current outcome. Registering the same listener again changes
 * nothing — the outcome is not replayed to someone who already heard it.
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
     * The place has nothing to show — a normal outcome, not a breakage: the config has no campaign
     * for the place, the targeting or the A/B split left the block out, the show limits are spent,
     * or the page reported no content. Why the place is empty stays with the campaign, so no
     * reason is given.
     *
     * The block already collapsed; [MindboxEmbeddedBlockView.setErrorView] does not apply to an
     * empty place. Nothing is required here: the block asks again by itself when it comes back on
     * screen and when a new config arrives.
     *
     * @param view The block left without content.
     */
    public fun onEmpty(view: MindboxEmbeddedBlockView) {}

    /**
     * The block could not get or show its content. An empty place is not a failure and arrives in
     * [onEmpty] instead.
     *
     * The block already collapsed, unless [MindboxEmbeddedBlockView.setErrorView] is set — then it
     * keeps its place and shows that view. Nothing is required here: the block retries by itself
     * when it comes back on screen.
     *
     * @param view The block that failed.
     * @param reason What went wrong, one of the [MindboxEmbeddedBlockFailReason] constants — for
     * the host's logs and analytics.
     */
    public fun onFail(view: MindboxEmbeddedBlockView, reason: MindboxEmbeddedBlockFailReason) {}
}
