package cloud.mindbox.mobile_sdk.network

import android.content.Context
import cloud.mindbox.mobile_sdk.BuildConfig
import cloud.mindbox.mobile_sdk.logOnException
import cloud.mindbox.mobile_sdk.models.MindboxRequest
import cloud.mindbox.mobile_sdk.returnOnException
import com.android.volley.RequestQueue
import com.android.volley.VolleyLog
import com.android.volley.toolbox.Volley

// Service generator as emulated singleton.
// Provides request queue and process requests
internal class MindboxServiceGenerator constructor(context: Context) {

    //Emulated singleton
    companion object {
        @Volatile
        private var INSTANCE: MindboxServiceGenerator? = null
        internal fun getInstance(context: Context): MindboxServiceGenerator? = runCatching {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MindboxServiceGenerator(context).also {
                    INSTANCE = it
                }
            }
        }.returnOnException { null }
    }

    init {
        runCatching {
            VolleyLog.DEBUG = BuildConfig.DEBUG
        }.logOnException()
    }

    private val requestQueue: RequestQueue? by lazy {
        // applicationContext is key, it keeps you from leaking the
        // Activity or BroadcastReceiver if someone passes one in.
        Volley.newRequestQueue(context.applicationContext)
    }

    internal fun addToRequestQueue(request: MindboxRequest) {
        runCatching {
            requestQueue?.add(request)
        }.returnOnException {}
    }
}