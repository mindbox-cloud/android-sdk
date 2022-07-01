package cloud.mindbox.mobile_sdk.network

import android.content.Context
import cloud.mindbox.mobile_sdk.BuildConfig
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.models.MindboxRequest
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import com.android.volley.RequestQueue
import com.android.volley.VolleyLog
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
            VolleyLog.DEBUG = BuildConfig.DEBUG
            Mindbox.mindboxScope.launch {
                bindRequestQueueWithMindboxScope()
            }
        }
    }

    private suspend fun bindRequestQueueWithMindboxScope() = suspendCancellableCoroutine<Unit> { continuation ->
        continuation.invokeOnCancellation {
            requestQueue?.cancelAll() { true }
        }
    }

    private val requestQueue: RequestQueue? by lazy {
        // applicationContext is key, it keeps you from leaking the
        // Activity or BroadcastReceiver if someone passes one in.
        Volley.newRequestQueue(context.applicationContext)
    }

    internal fun addToRequestQueue(request: MindboxRequest) = LoggingExceptionHandler.runCatching {
        requestQueue?.add(request)
    }
}