package cloud.mindbox.mobile_sdk.embedded

/**
 * Debug control over embedded block content — for the test app and acceptance testing.
 *
 * Overrides the answer to "what stands behind this place system name", that is, it takes exactly
 * the place of the mobile config's `inlineBlocks` section. Everything below — the content factory,
 * the page, its bridge, the container's waiting budget — works unchanged, so acceptance testing
 * exercises the production path rather than a separate test mode.
 *
 * Not part of the supported API and not covered by its compatibility promise. Deliberately not
 * stripped from release builds — QA checks exactly what ships to clients — which is why every
 * override that gets set is written to the log.
 *
 * Not annotated with `InternalMindboxApi` for the same reason [cloud.mindbox.mobile_sdk.embedded.mock.TempMindboxStoriesFeedMock]
 * is not: the marker lives in mindbox-common, which host apps do not see, and this object exists
 * precisely for a host app to call.
 */
public object MindboxEmbeddedBlockDebug {

    /** What to replace the block content with. */
    public sealed class Content {

        /**
         * A page url. This is how scenarios are run against the real network — including a
         * knowingly unreachable address, to get a load failure.
         */
        public data class Url(val url: String) : Content()

        /**
         * Ready-made markup. This is how scenarios that do not exist on the network are set up: a
         * page reporting "empty", a silent page, a page that answers after the timeout.
         */
        public data class Html(val html: String) : Content()

        /**
         * Nothing is attached to the place: the block is turned off in the admin panel or the place
         * system name is unknown.
         */
        public data object Empty : Content()
    }

    /**
     * Overrides the content of the block in this place. Applies to blocks that start loading after
     * the call: a block that is already shown has to be re-created or its screen reopened.
     */
    public fun setContent(content: Content, placeSystemName: String) {
        EmbeddedBlockContentOverrides.set(content, placeSystemName)
    }

    /** Gives the block in this place its usual content back. */
    public fun removeContent(placeSystemName: String) {
        EmbeddedBlockContentOverrides.remove(placeSystemName)
    }

    /** Drops every override at once. */
    public fun removeAllContent() {
        EmbeddedBlockContentOverrides.removeAll()
    }
}
