package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.app.Application
import cloud.mindbox.mobile_sdk.di.modules.AppModule
import cloud.mindbox.mobile_sdk.inapp.data.validators.HapticRequestValidator
import io.mockk.every
import io.mockk.mockk

internal fun AppModule.stubBridgeHelpers(
    context: Application,
    tagsResolver: OperationTagsResolver = OperationTagsResolver(mockk(relaxed = true), mockk(relaxed = true)),
    sender: WebViewOperationSender = MindboxWebViewOperationSender,
) {
    every { operationTagsResolver } returns tagsResolver
    every { webViewOperationSender } returns sender
    every { webViewLinkRouter } returns MindboxWebViewLinkRouter(context)
    every { webViewLocalStateStore } returns WebViewLocalStateStore(context)
    every { hapticRequestValidator } returns HapticRequestValidator()
}
