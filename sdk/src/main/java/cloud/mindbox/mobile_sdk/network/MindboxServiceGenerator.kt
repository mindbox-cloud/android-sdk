package cloud.mindbox.mobile_sdk.network

import android.content.Context
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.models.MindboxRequest
import cloud.mindbox.mobile_sdk.utils.BuildConfiguration
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.android.volley.RequestQueue
import com.android.volley.RequestQueue.RequestEvent.REQUEST_FINISHED
import com.android.volley.RequestQueue.RequestEvent.REQUEST_QUEUED
import com.android.volley.RequestQueue.RequestEventListener
import com.android.volley.VolleyLog
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

// Service generator as emulated singleton.
// Provides request queue and process requests
internal class MindboxServiceGenerator constructor(context: Context) {

    //Emulated singleton
    companion object {
        @Volatile
        private var INSTANCE: MindboxServiceGenerator? = null
        internal fun getInstance(
            context: Context,
        ): MindboxServiceGenerator? = LoggingExceptionHandler.runCatching(defaultValue = null) {
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: MindboxServiceGenerator(context).also {
                    INSTANCE = it
                }
            }
        }
    }

    init {
        LoggingExceptionHandler.runCatching {
            VolleyLog.DEBUG = BuildConfiguration.isDebug(context)
            Mindbox.mindboxScope.launch {
                bindRequestQueueWithMindboxScope()
            }
        }
    }

    private suspend fun bindRequestQueueWithMindboxScope() =
        suspendCancellableCoroutine<Unit> { continuation ->
            continuation.invokeOnCancellation {
                requestQueue?.cancelAll() { true }
            }
        }

    private val requestQueue: RequestQueue? by lazy {
        // applicationContext is key, it keeps you from leaking the
        // Activity or BroadcastReceiver if someone passes one in.
        Volley.newRequestQueue(context.applicationContext)
    }

    internal fun addToRequestQueue(request: StringRequest) {
        requestQueue?.add(request)
        // TODO change StringRequest to MindboxRequest or log here
    }

    internal fun addToMonitoringRequestQueue(request: MindboxRequest) {
        var startTime: Long? = null
        var requestEventListener: RequestEventListener? = null
        requestEventListener = RequestEventListener { queuedRequest, event ->
            if (event == REQUEST_QUEUED) {
                startTime = System.currentTimeMillis()

            } else if (request == queuedRequest && event == REQUEST_FINISHED) {
                requestQueue?.removeRequestEventListener(requestEventListener)
            } else {
                if (request == queuedRequest && startTime != null && (startTime!! + 5000) < System.currentTimeMillis()) {
                    requestQueue?.apply {
                        cancelAll { cancellableRequest -> cancellableRequest == request }
                    }
                }
            }
        }
        requestQueue?.addRequestEventListener(requestEventListener)
        requestQueue?.add(request)
    }

    internal fun addToRequestQueue(request: MindboxRequest) = LoggingExceptionHandler.runCatching {
        requestQueue?.let { requestQueue ->
            requestQueue.add(request)
            logMindboxRequest(request)
        }
    }

    private fun logMindboxRequest(request: MindboxRequest) {
        MindboxLoggerImpl.d(this, "MindboxRequest added to RequestQueue. " +
                "Method: ${request.methodType} Url: ${request.fullUrl} Request body: ${request.jsonRequest}")
    }
}