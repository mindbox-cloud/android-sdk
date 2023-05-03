package cloud.mindbox.mobile_sdk.di.modules

import cloud.mindbox.mobile_sdk.inapp.data.managers.InAppContentFetcherImpl
import cloud.mindbox.mobile_sdk.inapp.data.managers.InAppGlideImageLoaderImpl
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppContentFetcher
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageLoader
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.network.MindboxServiceGenerator
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley


internal fun ApiModule(
    appContextModule: AppContextModule,
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

    override val inAppContentFetcher: InAppContentFetcher
        get() = InAppContentFetcherImpl(inAppImageLoader)

    override val inAppImageLoader: InAppImageLoader
        get() = InAppGlideImageLoaderImpl(appContext)

}