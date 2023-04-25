package cloud.mindbox.mobile_sdk.di.modules

import cloud.mindbox.mobile_sdk.R
import cloud.mindbox.mobile_sdk.inapp.domain.InAppContentFetcherImpl
import cloud.mindbox.mobile_sdk.inapp.domain.InAppPicassoImageLoaderImpl
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppContentFetcher
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageLoader
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.network.MindboxServiceGenerator
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit


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

    override val picasso: Picasso by lazy {
        Picasso.Builder(appContext).downloader(
            OkHttp3Downloader(
                OkHttpClient.Builder().connectTimeout(
                    appContext.getString(R.string.mindbox_inapp_fetching_timeout).toLong(),
                    TimeUnit.SECONDS
                ).build()
            )
        ).build()
    }

    override val inAppContentFetcher: InAppContentFetcher
        get() = InAppContentFetcherImpl(inAppImageLoader)

    override val inAppImageLoader: InAppImageLoader
        get() = InAppPicassoImageLoaderImpl(picasso)

}