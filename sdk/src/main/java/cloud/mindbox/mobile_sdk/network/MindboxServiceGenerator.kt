package cloud.mindbox.mobile_sdk.network

import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.models.MindboxRequest
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.android.volley.RequestQueue
import com.android.volley.VolleyLog
import com.android.volley.toolbox.StringRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

internal class MindboxServiceGenerator(private val requestQueue: RequestQueue) {

    init {
        LoggingExceptionHandler.runCatching {
            VolleyLog.DEBUG = MindboxDI.appModule.isDebug()
            Mindbox.mindboxScope.launch {
                bindRequestQueueWithMindboxScope()
            }
        }
    }

    private suspend fun bindRequestQueueWithMindboxScope() =
        suspendCancellableCoroutine<Unit> { continuation ->
            continuation.invokeOnCancellation {
                requestQueue.cancelAll { true }
            }
        }

    internal fun addToRequestQueue(request: StringRequest) {
        requestQueue.add(request)
        // TODO change StringRequest to MindboxRequest or log here
    }

    internal fun addToRequestQueue(request: MindboxRequest) = LoggingExceptionHandler.runCatching {
        requestQueue.let { requestQueue ->
            requestQueue.add(request)
            logMindboxRequest(request)
        }
    }

    private fun logMindboxRequest(request: MindboxRequest) {
        LoggingExceptionHandler.runCatching {

            val builder = StringBuilder()
            builder.appendLine("---> Method: ${request.methodType} ${request.fullUrl}")
            builder.appendLine(request.headers
                .map { (key, value) -> "$key: $value" }
                .joinToString(separator =  System.getProperty("line.separator") ?:  "\n"))
            builder.appendLine("${request.jsonRequest}")
            builder.append("---> End of request")

            mindboxLogD(builder.toString())
        }
    }
}