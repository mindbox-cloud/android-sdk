package cloud.mindbox.mobile_sdk.network

import cloud.mindbox.mobile_sdk.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


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

    internal class HeaderRequestInterceptor : Interceptor {

        companion object {
            private const val HEADER_CONTENT_TYPE = "Content-Type"
            private const val HEADER_USER_AGENT = "User-Agent"
            private const val HEADER_INTEGRATION = "Mindbox-Integration"
            private const val HEADER_INTEGRATION_VERSION = "Mindbox-Integration-Version"

            private const val VALUE_CONTENT_TYPE = "application/json; charset=utf-8"
            private const val VALUE_USER_AGENT = "test.application.dev + 1.0.1, android + 11, Pixel, 4a"
            private const val VALUE_INTEGRATION = "Android-SDK"
            private const val VALUE_INTEGRATION_VERSION = "hardcoded_version.1.0.6"
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val newRequest: Request
            newRequest = request.newBuilder()
                .header(HEADER_CONTENT_TYPE, VALUE_CONTENT_TYPE)
                .header(HEADER_USER_AGENT, VALUE_USER_AGENT)
                .header(HEADER_INTEGRATION, VALUE_INTEGRATION)
                .header(HEADER_INTEGRATION_VERSION, VALUE_INTEGRATION_VERSION)
                .build()
            return chain.proceed(newRequest)
        }
    }
}