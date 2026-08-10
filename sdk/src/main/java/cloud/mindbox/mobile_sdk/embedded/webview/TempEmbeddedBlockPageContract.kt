package cloud.mindbox.mobile_sdk.embedded.webview

// Shared with deployed pages and the iOS SDK: renaming either value breaks every published page,
// so it has to happen together with the web team.
internal object TempEmbeddedBlockPageContract {

    const val BRIDGE_NAME: String = "mindboxStoriesFeed"

    const val DOM_READY_FLAG: String = "storiesReady"
}
