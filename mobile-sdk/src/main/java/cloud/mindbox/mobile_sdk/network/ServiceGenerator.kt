package cloud.mindbox.mobile_sdk.network

import android.content.Context
import cloud.mindbox.mobile_sdk.BuildConfig
import cloud.mindbox.mobile_sdk.models.MindboxRequest
import com.android.volley.RequestQueue
import com.android.volley.VolleyLog
import com.android.volley.toolbox.Volley

// Service generator as emulated singleton.
// Provides request queue and process requests
internal class ServiceGenerator constructor(context: Context) {

    //Emulated singleton
    companion object {
        @Volatile
        private var INSTANCE: ServiceGenerator? = null
        internal fun getInstance(context: Context) =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ServiceGenerator(context).also {
                    INSTANCE = it
                }
            }
    }

    init {
        VolleyLog.DEBUG = BuildConfig.DEBUG
    }

    internal val requestQueue: RequestQueue by lazy {
        // applicationContext is key, it keeps you from leaking the
        // Activity or BroadcastReceiver if someone passes one in.
        Volley.newRequestQueue(context.applicationContext)
    }

    internal fun addToRequestQueue(request: MindboxRequest) {
        requestQueue.add(request)
    }
}