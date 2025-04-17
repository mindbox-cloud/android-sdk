package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences

@SuppressLint("JavascriptInterface", "UNUSED")
internal class WebAppInterface(val configuration: Configuration, val onAction: (String, String) -> Unit) {

    @JavascriptInterface
    fun sdkVersion(): String = Mindbox.getSdkVersion()

    @JavascriptInterface
    fun endpointId(): String = configuration.endpointId

    @JavascriptInterface
    fun deviceUuid(): String = MindboxPreferences.deviceUuid

    @JavascriptInterface
    fun postMessage(action: String, data: String) {
        onAction(action, data)
    }
}
