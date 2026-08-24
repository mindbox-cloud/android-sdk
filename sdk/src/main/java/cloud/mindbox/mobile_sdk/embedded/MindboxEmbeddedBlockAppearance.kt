package cloud.mindbox.mobile_sdk.embedded

import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi

/**
 * How a [MindboxEmbeddedBlockView] occupies its place right now — what is drawn, not what happened.
 *
 * For wrappers that lay the block out themselves instead of relying on the view's own
 * `visibility`. The rules behind the decision stay here: that an empty place shows no error view,
 * that a place taken by loading is a place drawn, that a settled block keeps what it shows. A
 * wrapper mirrors the answer in its own layout and nothing more, so every wrapper of the SDK shows
 * the same thing at the same moment by construction.
 *
 * Deliberately not part of the public API: available to wrappers through [InternalMindboxApi].
 */
@InternalMindboxApi
public enum class MindboxEmbeddedBlockAppearance {

    /** The content is loading. The block takes its place and draws a loading screen. */
    PLACEHOLDER,

    /** The block content is shown. */
    CONTENT,

    /**
     * The block failed and the host opted into showing it by setting
     * [MindboxEmbeddedBlockView.setErrorView]. Never appears for an empty place.
     */
    ERROR,

    /**
     * The block occupies no space: a failure without a host error view, or an empty place. The
     * space goes back to the layout.
     */
    COLLAPSED,
}
