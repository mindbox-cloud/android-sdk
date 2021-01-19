package cloud.mindbox.mobile_sdk.models

import android.os.Build
import cloud.mindbox.mobile_sdk.BuildConfig
import cloud.mindbox.mobile_sdk.Configuration
import cloud.mindbox.mobile_sdk.Logger
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

data class MindboxRequest(
    val methodType: Int = Method.POST,
    val fullUrl: String = "",
    val configuration: Configuration,
    val operationType: String,
    val jsonRequest: JSONObject? = null,
    val listener: Response.Listener<JSONObject>? = null,
    val errorsListener: Response.ErrorListener? = null
) : JsonObjectRequest(methodType, fullUrl, jsonRequest, listener, errorsListener) {

    companion object {
        private const val HEADER_CONTENT_TYPE = "Content-Type"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val HEADER_INTEGRATION = "Mindbox-Integration"
        private const val HEADER_INTEGRATION_VERSION = "Mindbox-Integration-Version"

        private const val VALUE_CONTENT_TYPE = "application/json; charset=utf-8"
        private const val VALUE_USER_AGENT =
            "%1$1s + %2$1s(%3$1s), android + %4$1s, %5$1s, %6$1s" // format: {host.application.name + app_version(version_code), os + version, vendor, model}
        private const val VALUE_INTEGRATION = "Android-SDK"

        private const val QUERY_ENDPOINT = "endpointId"
        private const val QUERY_OPERATION = "operation"
        private const val QUERY_DEVICE_ID = "deviceUUID"
    }

    //building query parameters
    override fun getParams(): MutableMap<String, String> {
        val params: MutableMap<String, String> = HashMap()
        params[QUERY_ENDPOINT] = configuration.endpoint
        params[QUERY_OPERATION] = operationType
        params[QUERY_DEVICE_ID] = configuration.deviceId
        return params
    }

    //building headers
    override fun getHeaders(): MutableMap<String, String> {
        val params: MutableMap<String, String> = HashMap()

        params[HEADER_CONTENT_TYPE] = VALUE_CONTENT_TYPE
        params[HEADER_USER_AGENT] = String.format(
            VALUE_USER_AGENT,
            configuration.packageName,
            configuration.versionName,
            configuration.versionCode,
            Build.VERSION.RELEASE,
            Build.MANUFACTURER,
            Build.MODEL
        )
        params[HEADER_INTEGRATION] = VALUE_INTEGRATION
        params[HEADER_INTEGRATION_VERSION] = BuildConfig.VERSION_NAME

        return params
    }

    //Logging responses
    override fun parseNetworkResponse(response: NetworkResponse?): Response<JSONObject> {
        logResponse(response)

        return try {

            val json = String(
                response?.data ?: ByteArray(0),
                Charset.forName(HttpHeaderParser.parseCharset(response?.headers))
            )

            logBodyResponse(json)

            Response.success(
                JSONObject(json), HttpHeaderParser.parseCacheHeaders(response)
            )
        } catch (e: UnsupportedEncodingException) {

            Response.error(ParseError(e))
        } catch (e: JsonSyntaxException) {
            Response.error(ParseError(e))
        } finally {
            logEndResponse()
        }
    }

    //Logging error responses
    override fun parseNetworkError(volleyError: VolleyError): VolleyError {
        Logger.d(
            this,
            "<--- Error ${volleyError.networkResponse?.statusCode} $fullUrl TimeMls:${volleyError.networkTimeMs}; "
        )
        try {

            volleyError.networkResponse?.allHeaders?.forEach { header ->
                Logger.d(this, "${header.name}: ${header.value}")
            }

            val json = String(
                volleyError.networkResponse?.data ?: ByteArray(0),
                Charset.forName(HttpHeaderParser.parseCharset(volleyError.networkResponse?.headers))
            )

            logBodyResponse(json)
        } catch (e: java.lang.Exception) {
            logError(e)
        } finally {
            logEndResponse()
        }
        return volleyError
    }

    private fun logResponse(response: NetworkResponse?) {
        Logger.d(this, "<--- ${response?.statusCode} $fullUrl")

        response?.allHeaders?.forEach { header ->
            Logger.d(this, "${header.name}: ${header.value}")
        }
    }

    private fun logBodyResponse(json: String?) {
        Logger.d(this, "$json")
    }

    private fun logError(e: Exception) {
        Logger.d(this, e.message ?: "Empty message")
        Logger.d(this, e.stackTraceToString())
    }

    private fun logEndResponse() {
        Logger.d(this, "<--- End of response")
    }
}