package cloud.mindbox.mobile_sdk.models

import android.os.Build
import cloud.mindbox.mobile_sdk.*
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import com.android.volley.NetworkResponse
import com.android.volley.ParseError
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.JsonObjectRequest
import com.google.gson.JsonSyntaxException
import org.json.JSONObject
import java.io.UnsupportedEncodingException
import java.nio.charset.Charset

internal data class MindboxRequest(
    val methodType: Int = Method.POST,
    val fullUrl: String = "",
    val configuration: Configuration,
    val jsonRequest: JSONObject? = null,
    val listener: Response.Listener<JSONObject>? = null,
    val errorsListener: Response.ErrorListener? = null
) : JsonObjectRequest(methodType, fullUrl, jsonRequest, listener, errorsListener) {

    companion object {
        private const val HEADER_CONTENT_TYPE = "Content-Type"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val HEADER_INTEGRATION = "Mindbox-Integration"
        private const val HEADER_INTEGRATION_VERSION = "Mindbox-Integration-Version"
        private const val HEADER_ACCEPT = "Accept"

        private const val VALUE_CONTENT_TYPE = "application/json; charset=utf-8"
        private const val VALUE_USER_AGENT =
            "mindbox.sdk/%1$1s (Android %2$1s; %3$1s; %4$1s) %5$1s/%6$1s(%7$1s)" // format: mindbox.sdk/{sdk.version} (Android {os_version}; {vendor}; {model}) {host_app_name}/{host_app_version}
        private const val VALUE_INTEGRATION = "Android-SDK"
        private const val VALUE_ACCEPT = "application/json"

        private const val DEFAULT_RESPONSE_CHARSET = "UTF-8"
    }

    //building headers
    override fun getHeaders(): MutableMap<String, String> {
        val params: MutableMap<String, String> = HashMap()

        runCatching {
            params[HEADER_CONTENT_TYPE] = VALUE_CONTENT_TYPE
            params[HEADER_USER_AGENT] = String.format(
                VALUE_USER_AGENT,
                BuildConfig.VERSION_NAME,
                Build.VERSION.RELEASE,
                Build.MANUFACTURER,
                Build.MODEL,
                configuration.packageName,
                configuration.versionName,
                configuration.versionCode
            )
            params[HEADER_INTEGRATION] = VALUE_INTEGRATION
            params[HEADER_INTEGRATION_VERSION] = BuildConfig.VERSION_NAME
            params[HEADER_ACCEPT] = VALUE_ACCEPT
        }.logOnException()

        return params
    }

    //Logging responses
    override fun parseNetworkResponse(response: NetworkResponse?): Response<JSONObject> {
        return runCatching {
            logResponse(response)

            try {

                val body = String(
                    response?.data ?: ByteArray(0),
                    Charset.forName(
                        HttpHeaderParser.parseCharset(
                            response?.headers,
                            DEFAULT_RESPONSE_CHARSET
                        )
                    )
                )

                logBodyResponse(body)

                val bodyJson = when {
                    body.isEmpty() -> "{data: null}"
                    !body
                        .replace("\n", "")
                        .matches("^\\{.*\\}$".toRegex()) -> "{data: $body}"
                    else -> body
                }

                val cacheEntry = if (response != null) {
                    HttpHeaderParser.parseCacheHeaders(response)
                } else null

                Response.success(JSONObject(bodyJson), cacheEntry)
            } catch (e: UnsupportedEncodingException) {
                Response.error(ParseError(e))
            } catch (e: JsonSyntaxException) {
                Response.error(ParseError(e))
            } finally {
                logEndResponse()
            }
        }.returnOnException { e -> Response.error(ParseError(e)) }
    }

    //Logging error responses
    override fun parseNetworkError(volleyError: VolleyError): VolleyError {
        if (BuildConfig.DEBUG) {
            runCatching {
                MindboxLogger.e(
                    this,
                    "<--- Error ${volleyError.networkResponse?.statusCode} $fullUrl TimeMls:${volleyError.networkTimeMs}; "
                )
                try {

                    volleyError.networkResponse?.allHeaders?.forEach { header ->
                        MindboxLogger.d(this, "${header.name}: ${header.value}")
                    }

                    val json = String(
                        volleyError.networkResponse?.data ?: ByteArray(0),
                        Charset.forName(
                            HttpHeaderParser.parseCharset(
                                volleyError.networkResponse?.headers ?: emptyMap()
                            )
                        )
                    )

                    logBodyResponse(json)
                } catch (e: Exception) {
                    logError(e)
                } finally {
                    logEndResponse()
                }
            }.logOnException()
        }
        return volleyError
    }

    private fun logResponse(response: NetworkResponse?) {
        if (BuildConfig.DEBUG) {
            runCatching {
                MindboxLogger.d(this, "<--- ${response?.statusCode} $fullUrl")

                response?.allHeaders?.forEach { header ->
                    MindboxLogger.d(this, "${header.name}: ${header.value}")
                }
            }.returnOnException { }
        }
    }

    private fun logBodyResponse(json: String?) {
        if (BuildConfig.DEBUG) {
            runCatching {
                MindboxLogger.d(this, "$json")
            }.logOnException()
        }
    }

    private fun logError(e: Exception) {
        runCatching {
            MindboxLogger.d(this, e.message ?: "Empty message")
            MindboxLogger.d(this, e.stackTraceToString())
        }.logOnException()
    }

    private fun logEndResponse() {
        if (BuildConfig.DEBUG) {
            runCatching {
                MindboxLogger.d(this, "<--- End of response")
            }.logOnException()
        }
    }

}
