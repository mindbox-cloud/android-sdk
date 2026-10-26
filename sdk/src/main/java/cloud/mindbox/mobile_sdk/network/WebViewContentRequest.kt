package cloud.mindbox.mobile_sdk.network

import androidx.annotation.VisibleForTesting
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import com.android.volley.NetworkResponse
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest

internal class WebViewContentRequest(
    url: String,
    listener: Response.Listener<String>,
    errorListener: Response.ErrorListener,
) : StringRequest(Method.GET, url, listener, errorListener) {

    init {
        setShouldCache(false)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public override fun parseNetworkResponse(response: NetworkResponse?): Response<String> {
        loggingRunCatching { mindboxLogD(describe(response)) }
        return super.parseNetworkResponse(response)
    }

    private fun describe(response: NetworkResponse?): String {
        if (response == null) return "<--- no response for $url"
        val cacheHeaders = response.allHeaders.orEmpty()
            .filter { header -> header.name.lowercase() in LOGGED_HEADERS }
            .joinToString(separator = ", ") { header -> "${header.name}: ${header.value}" }
        return buildString {
            append("<--- ${response.statusCode} $url in ${response.networkTimeMs} ms, ${response.data?.size ?: 0} bytes")
            if (cacheHeaders.isNotEmpty()) append(" [$cacheHeaders]")
        }
    }

    private companion object {
        val LOGGED_HEADERS = setOf("cache-status", "cache-host", "etag", "last-modified", "cache-control")
    }
}
