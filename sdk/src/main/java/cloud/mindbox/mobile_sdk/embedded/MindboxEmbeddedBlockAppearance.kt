package cloud.mindbox.mobile_sdk.embedded

import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi

/**
 * How the block occupies its place right now — the contract between the container and a wrapper that
 * lays the block out itself.
 *
 * A View or Compose host needs none of this: the block sets its own visibility and the host's own
 * placeholder and error view go straight into it. A cross-platform wrapper cannot do either. A Flutter
 * widget has no Android View behind it, so it cannot be handed over — the container is told only that
 * the place is taken, and the widget is drawn by Flutter over the platform view, which also takes its
 * size from Dart rather than from the block.
 *
 * So a wrapper is told what to show, not what happened. Everything behind the decision — the content
 * states, the rule that an empty place shows no error view — stays in the container, and the same
 * answer serves every wrapper.
 */
@InternalMindboxApi
public enum class MindboxEmbeddedBlockAppearance {

    /** The content is loading: a wrapper with a loading screen of its own draws it. */
    PLACEHOLDER,

    /** The block content is shown — the wrapper draws nothing over it. */
    CONTENT,

    /**
     * The block failed and the host opted into showing it: the wrapper draws its error screen. Never
     * appears for an empty place.
     */
    ERROR,

    /**
     * The block occupies no space: a failure without an error view, or an empty place. The wrapper
     * gives the space back to the layout.
     */
    COLLAPSED,
}
