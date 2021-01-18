package cloud.mindbox.mobile_sdk.models

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
    val jsonRequest: JSONObject? = null,
    val listener: Response.Listener<JSONObject>? = null,
    val errorsListener: Response.ErrorListener? = null
) : JsonObjectRequest(methodType, fullUrl, jsonRequest, listener, errorsListener) {

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