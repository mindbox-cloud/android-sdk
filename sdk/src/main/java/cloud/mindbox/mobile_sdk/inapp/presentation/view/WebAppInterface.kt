package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import cloud.mindbox.mobile_sdk.logger.mindboxLogI

@SuppressLint("JavascriptInterface", "UNUSED")
internal class WebAppInterface(
    private val paramsProvider: ParamProvider,
    private val onAction: (String, String) -> Unit
) {

    @JavascriptInterface
    fun receiveParam(key: String): String? {
        return paramsProvider.get(key).also {
            mindboxLogI("Call receiveParam key: $key, return: $it")
        }
    }

    @JavascriptInterface
    fun postMessage(action: String, data: String) {
        mindboxLogI("Call postMessage action: $action, data: $data")
        onAction(action, data)
    }
}

internal fun interface ParamProvider {
    fun get(key: String): String?
}
