package cloud.mindbox.mobile_sdk.di.modules

import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.network.MindboxServiceGenerator
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley

internal fun ApiModule(
    appContextModule: AppContextModule
): ApiModule = object : ApiModule,
    AppContextModule by appContextModule {

    override val gatewayManager by lazy {
        GatewayManager(mindboxServiceGenerator)
    }

    override val mindboxServiceGenerator by lazy {
        MindboxServiceGenerator(requestQueue)
    }

    override val requestQueue: RequestQueue by lazy {
        Volley.newRequestQueue(appContext)
    }
}
