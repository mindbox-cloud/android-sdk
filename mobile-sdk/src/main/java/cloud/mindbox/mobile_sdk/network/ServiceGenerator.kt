package cloud.mindbox.mobile_sdk.network

import android.util.Log
import cloud.mindbox.mobile_sdk.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException


internal object ServiceGenerator {

    private const val BASE_URL_PLACEHOLDER = "https://%s/"

    private val client: OkHttpClient = initClient()

    fun initRetrofit(domain: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(String.format(BASE_URL_PLACEHOLDER, domain))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun initClient(): OkHttpClient {
        return OkHttpClient.Builder().apply {

            addInterceptor(HeaderRequestInterceptor())

            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
            }
        }
            .build()
    }

    internal class HeaderRequestInterceptor: Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val newRequest: Request
            newRequest = request.newBuilder()
                .header("Content-Type", "application/json; charset=utf-8")
                .header("User-Agent", "test.application.dev + 1.0.1, android + 11, Pixel, 4a")
                .header("Mindbox-Integration", "Android-SDK")
                .header("Mindbox-Integration-Version", "hardcoded_version.1.0.6")
                .build()
            Log.i("Interceptor debug", " its working")
            return chain.proceed(newRequest)
        }
    }
}