package com.mindbox.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.logger.Level
import com.mindbox.example.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class MainActivity : AppCompatActivity() {

    companion object {
        const val URL = "https://your-website.com/"
        const val FETCHING_DEVICE_UUID_TIMEOUT = 4000L
    }

    private lateinit var webView: WebView
    private var deviceUUID: String? = null
    private var _binding: ActivityMainBinding? = null
    private val binding: ActivityMainBinding
        get() = _binding!!


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Use this line to enable debugging
        WebView.setWebContentsDebuggingEnabled(true)

        //initialize webview after Mindbox.init if init in activity
        webView = binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = webViewClientInstance
        }

        /***
         * Start loading the page after obtaining the deviceUUID.
         * On the first app launch, obtaining the deviceUUID may take several seconds.
         * If you don't wait for the deviceUUID, synchronization will occur on the next page load.
         * The waiting time can be adjusted in the GET_DEVICE_UUID_TIMEOUT constant.
         ***/
        CoroutineScope(Dispatchers.Main).launch {
            try {
                deviceUUID = withContext(Dispatchers.IO) {
                    getDeviceUUID()
                }
                Mindbox.writeLog("DeviceUUID for synchronization received: $deviceUUID", logLevel = Level.DEBUG)
                webView.loadUrl(URL)
            } catch (e: TimeoutCancellationException) {
                Mindbox.writeLog("Timeout while waiting for synchronization Device UUID. Loading without UUID", logLevel = Level.DEBUG)
                webView.loadUrl(URL)
            } catch (e: Exception) {
                Mindbox.writeLog("Failed to get Device UUID for synchronization: ${e.message}", logLevel = Level.ERROR)
                webView.loadUrl(URL)
            }
        }

        binding.viewCookiesButton.setOnClickListener {
            showCookies()
        }

        processMindboxIntent(intent = intent, context = this)?.let { (url, payload) ->
            Log.d(Utils.TAG, "Data from push: url: $url, payload: $payload")
        }

    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processMindboxIntent(intent = intent, context = this)?.let { (url, payload) ->
            Log.d(Utils.TAG, "Data from push: url: $url, payload: $payload")
        }
        Mindbox.onNewIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    private val webViewClientInstance: WebViewClient by lazy {

        object : WebViewClient() {

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d(Utils.TAG, "Page started loading: $url")
                // Synchronizing deviceUUID
                deviceUUID?.let {
                    syncMindboxDeviceUUIDs(it)
                } ?: run {
                    Mindbox.subscribeDeviceUuid { uuid ->
                        if (uuid.isNotEmpty()) {
                            deviceUUID = uuid
                            syncMindboxDeviceUUIDs(uuid)
                        }
                    }
                }
            }
        }
    }

    // Getting device UUID by mindbox mobile sdk
    private suspend fun getDeviceUUID(): String = withTimeout(FETCHING_DEVICE_UUID_TIMEOUT) {
        suspendCancellableCoroutine { continuation ->
            Mindbox.subscribeDeviceUuid { uuid ->
                if (uuid.isNotEmpty()) {
                    continuation.resume(uuid)
                } else {
                    continuation.resumeWithException(Exception("Device UUID is empty"))
                }
            }
        }
    }

    // Synchronize deviceUUID
    private fun syncMindboxDeviceUUIDs(uuid: String) {
        webView.evaluateJavascript(
            """
            document.cookie = "mindboxDeviceUUID=$uuid";
            window.localStorage.setItem('mindboxDeviceUUID', '$uuid');
            """
        ) {
            Mindbox.writeLog("Device UUID synchronized with deviceUUID: $uuid", logLevel = Level.DEBUG)
        }
    }

    // Use it to debug data after tracker initialize
    // For example add button for debug
    private fun showCookies() {
        val cookies = CookieManager.getInstance().getCookie(URL)
        Log.d(Utils.TAG, "Cookies: $cookies")
        Mindbox.subscribeDeviceUuid { uuid ->
            Log.d(Utils.TAG, "mobile sdk deviceUUID=$uuid")
        }
        webView.evaluateJavascript(
            "(function() {return window.localStorage.getItem('mindboxDeviceUUID')})()"
        ) { result ->
            Log.d(Utils.TAG, "js sdk deviceUUID: $result")
        }
    }

    // Use this method to clear WebView cache
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun clearAllCookies() {

        WebStorage.getInstance().deleteAllData()
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies { success ->
            if (success) {
                Log.d(Utils.TAG, "All cookies cleared")
            } else {
                Log.e(Utils.TAG, "Failed to clear cookies")
            }
        }
    }
}
