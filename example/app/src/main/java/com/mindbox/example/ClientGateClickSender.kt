package com.mindbox.example

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import cloud.mindbox.mobile_sdk.getMindboxUniqKeyFromPushIntent
import cloud.mindbox.mobile_sdk.getMindboxUniqPushButtonKeyFromPushIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ClientGateClickSender {

    private const val MAX_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 2_000L
    private const val TIMEOUT_MS = 10_000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sentClicks = mutableSetOf<String>()

    fun sendClick(context: Context, intent: Intent) {
        val messageId = intent.getMindboxUniqKeyFromPushIntent() ?: return
        val clickKey = intent.getMindboxUniqPushButtonKeyFromPushIntent()
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) {
            Log.d(Utils.TAG, "Push intent relaunched from recents, click is not sent again: $messageId")
            return
        }
        if (!synchronized(sentClicks) { sentClicks.add("$messageId|$clickKey") }) {
            Log.d(Utils.TAG, "Click already sent to client gate: messageId=$messageId, clickKey=$clickKey")
            return
        }
        val clickUrl = BuildConfig.CLIENT_GATE_CLICK_URL
        val token = BuildConfig.CLIENT_GATE_TOKEN
        if (clickUrl.isBlank() || token.isBlank()) {
            Log.w(Utils.TAG, "clientGate.clickUrl or clientGate.token is empty, click is not sent: messageId=$messageId")
            return
        }
        val body = JSONObject()
            .put("messageId", messageId)
            .apply { clickKey?.let { put("clickKey", it) } }
            .put("occurredAtUtc", utcNow())
            .toString()
        val appContext = context.applicationContext
        scope.launch { send(appContext, clickUrl, token, body) }
    }

    private suspend fun send(context: Context, clickUrl: String, token: String, body: String) {
        repeat(MAX_ATTEMPTS) { index ->
            val attempt = index + 1
            val code = try {
                post(clickUrl, token, body)
            } catch (e: IOException) {
                Log.w(Utils.TAG, "Click to client gate failed, attempt $attempt/$MAX_ATTEMPTS: $body", e)
                null
            }
            when {
                code == null -> Unit
                code in 200..299 -> {
                    Log.i(Utils.TAG, "Click sent to client gate, response $code: $body")
                    return
                }
                code == HttpURLConnection.HTTP_UNAUTHORIZED -> {
                    Log.e(Utils.TAG, "Client gate answered 401, clientGate.token has expired: $body")
                    Handler(Looper.getMainLooper()).post {
                        showToast(context, context.getString(R.string.toast_client_gate_unauthorized))
                    }
                    return
                }
                code >= 500 -> Log.w(Utils.TAG, "Client gate answered $code, attempt $attempt/$MAX_ATTEMPTS: $body")
                else -> {
                    Log.e(Utils.TAG, "Client gate rejected the click with $code: $body")
                    return
                }
            }
            if (attempt < MAX_ATTEMPTS) delay(RETRY_DELAY_MS * attempt)
        }
        Log.e(Utils.TAG, "Click is lost after $MAX_ATTEMPTS attempts: $body")
    }

    private fun post(clickUrl: String, token: String, body: String): Int {
        val connection = URL(clickUrl).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            return connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    private fun utcNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())
}
