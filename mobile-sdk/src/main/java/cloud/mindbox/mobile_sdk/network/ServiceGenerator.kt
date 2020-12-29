package cloud.mindbox.mobile_sdk.network

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

    private const val BASE_URL = "https://api.mindbox.ru/v3/operations/"

    private val client: OkHttpClient = initClient()

    fun initRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun initClient(): OkHttpClient {
        return OkHttpClient.Builder().apply {

            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
                // todo fix and return on next tickets
//                addInterceptor(HeaderRequestInterceptor())
            }
        }
            .build()
    }

    internal class HeaderRequestInterceptor : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest: Request = chain.request()
            if (originalRequest.body == null) {
                return chain.proceed(originalRequest)
            }
            val compressedRequest: Request = originalRequest.newBuilder()
                //todo add complete headers
                .header("Mindbox-Integration", "Android-SDK")
                .header("Mindbox-Integration-Version", "some version")
                .method(originalRequest.method, originalRequest.body!!)
                .build()
            return chain.proceed(compressedRequest)
        }
    }


}