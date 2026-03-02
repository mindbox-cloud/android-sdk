package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import com.google.gson.JsonParser

internal interface WebViewLinkRouter {
    fun executeOpenLink(request: String?): Result<String>
}

internal class MindboxWebViewLinkRouter(
    private val context: Context,
) : WebViewLinkRouter {

    companion object {
        private const val SCHEME_HTTP = "http"
        private const val SCHEME_HTTPS = "https"
        private const val SCHEME_INTENT = "intent"
        private const val SCHEME_TEL = "tel"
        private const val SCHEME_MAILTO = "mailto"
        private const val SCHEME_SMS = "sms"
        private const val KEY_URL = "url"
        private val BLOCKED_SCHEMES: Set<String> = setOf("javascript", "file", "data", "blob")
        private const val ERROR_MISSING_URL = "Invalid payload: missing or empty 'url' field"
    }

    override fun executeOpenLink(request: String?): Result<String> {
        return runCatching {
            val url: String = extractTargetUrl(request)
            val parsedUri = parseUrl(url)
            routeByScheme(
                parsedUri = parsedUri,
                targetUrl = url,
            )
        }
    }

    private fun extractTargetUrl(request: String?): String {
        if (request.isNullOrBlank()) {
            throw IllegalStateException(ERROR_MISSING_URL)
        }
        val parsedJsonElement = runCatching { JsonParser.parseString(request) }.getOrNull()
            ?: throw IllegalStateException(ERROR_MISSING_URL)
        if (!parsedJsonElement.isJsonObject) {
            throw IllegalStateException(ERROR_MISSING_URL)
        }
        val url: String = parsedJsonElement.asJsonObject.get(KEY_URL)?.asString?.trim().orEmpty()
        if (url.isBlank()) {
            throw IllegalStateException(ERROR_MISSING_URL)
        }
        return url
    }

    private fun parseUrl(url: String): Uri {
        val parsedUri: Uri = url.toUri()
        val scheme: String = parsedUri.scheme?.lowercase().orEmpty()
        if (scheme.isBlank()) {
            throw IllegalStateException("Invalid URL: '$url' could not be parsed")
        }
        if (scheme in BLOCKED_SCHEMES) {
            throw IllegalStateException("Blocked URL scheme: '$scheme'")
        }
        return parsedUri
    }

    private fun routeByScheme(
        parsedUri: Uri,
        targetUrl: String,
    ): String {
        val scheme = parsedUri.scheme
        requireNotNull(scheme) { "Url scheme must be not null" }
        return when (scheme.lowercase()) {
            SCHEME_INTENT -> openIntentUri(targetUrl)
            SCHEME_TEL -> openDialLink(parsedUri, targetUrl)
            SCHEME_SMS, SCHEME_MAILTO -> openSendToLink(parsedUri, targetUrl)
            SCHEME_HTTP, SCHEME_HTTPS -> openUriWithViewIntent(parsedUri, targetUrl)
            else -> openUriWithViewIntent(parsedUri, targetUrl)
        }
    }

    private fun openIntentUri(rawIntentUri: String): String {
        val parsedIntent: Intent = runCatching { Intent.parseUri(rawIntentUri, Intent.URI_INTENT_SCHEME) }
            .getOrElse {
                mindboxLogW("Intent URI parse failed: $rawIntentUri")
                throw IllegalStateException("Invalid URL: '$rawIntentUri' could not be parsed")
            }
        if (parsedIntent.action.isNullOrBlank()) {
            parsedIntent.action = Intent.ACTION_VIEW
        }
        parsedIntent.selector = null
        parsedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return startIntent(parsedIntent, rawIntentUri)
    }

    private fun openDialLink(uri: Uri, rawUrl: String): String {
        val dialIntent: Intent = Intent(Intent.ACTION_DIAL, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startIntent(dialIntent, rawUrl)
    }

    private fun openSendToLink(uri: Uri, rawUrl: String): String {
        val smsIntent: Intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startIntent(smsIntent, rawUrl)
    }

    private fun openUriWithViewIntent(uri: Uri, rawUrl: String): String {
        val intent: Intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startIntent(intent, rawUrl)
    }

    private fun startIntent(intent: Intent, rawUrl: String): String {
        return try {
            context.startActivity(intent)
            rawUrl
        } catch (error: ActivityNotFoundException) {
            mindboxLogW("Activity not found for URI: $rawUrl")
            throw IllegalStateException(
                "ActivityNotFoundException: ${error.message ?: "No activity found to handle URL"}"
            )
        } catch (error: SecurityException) {
            mindboxLogW("Security exception for URI: $rawUrl")
            throw IllegalStateException(
                "SecurityException: ${error.message ?: "Cannot open URL"}"
            )
        } catch (error: Throwable) {
            throw IllegalStateException(error.message ?: "Navigation failed: unable to open URL")
        }
    }
}
