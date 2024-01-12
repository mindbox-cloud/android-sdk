package cloud.mindbox.mobile_sdk.models

import android.os.Build
import cloud.mindbox.mobile_sdk.BuildConfig
import cloud.mindbox.mobile_sdk.getErrorResponseBodyData
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
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
    val errorsListener: Response.ErrorListener? = null,
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

        LoggingExceptionHandler.runCatching {
            params[HEADER_CONTENT_TYPE] = VALUE_CONTENT_TYPE
            params[HEADER_USER_AGENT] = String.format(
                VALUE_USER_AGENT,
                BuildConfig.VERSION_NAME,
                Build.VERSION.RELEASE,
                Build.MANUFACTURER,
                Build.MODEL,
                configuration.packageName,
                configuration.versionName,
                configuration.versionCode,
            )
            params[HEADER_INTEGRATION] = VALUE_INTEGRATION
            params[HEADER_INTEGRATION_VERSION] = BuildConfig.VERSION_NAME
            params[HEADER_ACCEPT] = VALUE_ACCEPT
        }

        return params
    }

    //Logging responses
    override fun parseNetworkResponse(response: NetworkResponse?): Response<JSONObject> {
        return LoggingExceptionHandler.runCatching(
            block = {
                logResponse(response)

                try {

                    val body = String(
                        response?.data ?: ByteArray(0),
                        Charset.forName(
                            HttpHeaderParser.parseCharset(
                                response?.headers,
                                DEFAULT_RESPONSE_CHARSET,
                            ),
                        ),
                    )

                    logBodyResponse(body)

                    val bodyJson = when {
                        body.isEmpty() -> "{data: null}"
                        !isJsonObject(body) -> "{data: $body}"
                        else -> body
                    }

                    val cacheEntry = response?.let(HttpHeaderParser::parseCacheHeaders)

                    Response.success(JSONObject(bodyJson), cacheEntry)
                } catch (e: UnsupportedEncodingException) {
                    Response.error(ParseError(e))
                } catch (e: JsonSyntaxException) {
                    Response.error(ParseError(e))
                } finally {
                    logEndResponse()
                }
            },
            defaultValue = { e -> Response.error(ParseError(e)) }
        )
    }

    //Logging error responses
    override fun parseNetworkError(volleyError: VolleyError): VolleyError {
        LoggingExceptionHandler.runCatching {
            try {
                val json = volleyError.getErrorResponseBodyData()

                logResponseResult(volleyError, json)

                volleyError.networkResponse?.allHeaders?.joinToString(
                    separator = System.getProperty("line.separator") ?: "\n"
                ) { header ->
                    "${header.name}: ${header.value}"
                }?.let { mindboxLogI(it) }

                logBodyResponse(json)
            } catch (e: Exception) {
                logError(e)
            } finally {
                logEndResponse()
            }
        }
        return volleyError
    }

    private fun isJsonObject(body: String) = body.startsWith("{") && body.endsWith("}")

    private fun logResponse(response: NetworkResponse?) {
        LoggingExceptionHandler.runCatching {
            mindboxLogI("<--- ${response?.statusCode} $fullUrl")

            response?.allHeaders?.joinToString(
                separator = System.getProperty("line.separator") ?: "\n"
            ) { header ->
                "${header.name}: ${header.value}"
            }?.let { mindboxLogI(it) }
        }
    }

    private fun logBodyResponse(json: String?) {
        LoggingExceptionHandler.runCatching {
            mindboxLogI("$json")
        }
    }

    private fun logError(e: Exception) {
        LoggingExceptionHandler.runCatching {
            mindboxLogW(e.message ?: "Empty message")
            mindboxLogW(e.stackTraceToString())
        }
    }

    private fun logEndResponse() {
        LoggingExceptionHandler.runCatching {
            mindboxLogI("<--- End of response")
        }
    }

    private fun logResponseResult(volleyError: VolleyError?, json: String?) {
        LoggingExceptionHandler.runCatching {
            val logMessage = buildString {
                append("<--- ")
                append(if (json?.contains("\"status\": \"Success\"") == true) "Success" else "Error")
                append(" ${volleyError?.networkResponse?.statusCode} $fullUrl TimeMls:${volleyError?.networkTimeMs}; ")
            }
            if (json?.contains("{\"status\": \"Success\"}") == true) {
                mindboxLogI(logMessage)
            } else {
                mindboxLogW(logMessage)
            }
        }
    }

}
