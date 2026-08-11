package cloud.mindbox.mobile_sdk.embedded.webview

internal object TempEmbeddedBlockPageContract {

    // Matches the iOS handler name: one page speaks to both platforms, so the name it posts to
    // cannot differ between them. Renaming it again means renaming it on iOS and in every
    // published page, so it happens together with the web team.
    const val BRIDGE_NAME: String = "mindboxEmbeddedBlock"

    // Android-only, and specific to the stories page that does not implement the contract yet:
    // it never posts anything, so readiness is read off this DOM flag instead. Goes away with the
    // first page that sends `ready` by itself.
    const val DOM_READY_FLAG: String = "storiesReady"
}
