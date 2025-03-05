package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import com.google.gson.Gson
import java.util.UUID

@SuppressLint("JavascriptInterface")
class WebAppInterface(val onAction: (RequestBody) -> Unit) {

    @JavascriptInterface
    fun sdkVersion(): String = Mindbox.getSdkVersion()

    @JavascriptInterface
    fun endpointId(): String = "Test-staging.mobile-sdk-test-staging.mindbox.ru"

    @JavascriptInterface
    fun deviceUuid(): String = UUID.randomUUID().toString() // MindboxPreferences.deviceUuid

    @JavascriptInterface
    fun resize(height: Float) {
        mindboxLogI("WEBVIEW ResizeWebView: $height")
    }

    @JavascriptInterface
    fun sendXHRRequestData(url: String, requestBody: String) {
        // Handle the captured request body
        mindboxLogI("CapturedRequestBody: $url $requestBody")
        if ("https://quizzes-staging.mindbox.ru/api/v0/actions" == url) {
            mindboxLogI("CapturedRequestBody: $requestBody")
            val body = gson.fromJson(requestBody, RequestBody::class.java)
            onAction(body)
        }

        // You can now process this data, such as sending it to a server or logging it
    }

    val gson by lazy { Gson() }

    // {"mode":"quiz","screen":"minimal","slug":"televisions","action":"show"}
    data class RequestBody(val mode: String, val screen: String, val slug: String, val action: String)
}
