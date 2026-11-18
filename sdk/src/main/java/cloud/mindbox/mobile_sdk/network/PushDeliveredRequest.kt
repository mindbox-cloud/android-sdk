package cloud.mindbox.mobile_sdk.network

import androidx.annotation.VisibleForTesting
import cloud.mindbox.mobile_sdk.getErrorResponseBodyData
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.logger.mindboxLogW
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.MindboxRequest
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.VolleyError

internal class PushDeliveredRequest(
    url: String,
    private val configuration: Configuration,
    private val onDelivered: () -> Unit,
    errorListener: Response.ErrorListener,
) : Request<Unit>(Method.POST, url, errorListener) {

    companion object {
        const val TIMEOUT_MS = 10_000
        private const val MAX_RETRIES = 0
    }

    init {
        setShouldCache(false)
        retryPolicy = DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
    }

    override fun getHeaders(): MutableMap<String, String> = MindboxRequest.buildHeaders(configuration)

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public override fun parseNetworkResponse(response: NetworkResponse?): Response<Unit> {
        loggingRunCatching { mindboxLogI("<--- ${response?.statusCode} $url in ${response?.networkTimeMs} ms") }
        return Response.success(Unit, null)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public override fun deliverResponse(response: Unit) {
        onDelivered()
    }

    override fun parseNetworkError(volleyError: VolleyError): VolleyError {
        loggingRunCatching {
            mindboxLogW("<--- ${volleyError.networkResponse?.statusCode} $url in ${volleyError.networkTimeMs} ms")
            volleyError.getErrorResponseBodyData().takeIf { it.isNotBlank() }?.let { body -> mindboxLogI(body) }
        }
        return volleyError
    }
}
