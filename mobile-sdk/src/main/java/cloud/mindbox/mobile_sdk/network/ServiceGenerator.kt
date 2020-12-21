package cloud.mindbox.mobile_sdk.network

import cloud.mindbox.mobile_sdk.BuildConfig
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.experimental.CoroutineCallAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException


internal object ServiceGenerator {

    private const val BASE_URL = "https://api.mindbox.ru/v3/operations"

    val mindboxApi = initRetrofit().create(RestApi::class.java)
    private val retrofit: Retrofit
    private val client: OkHttpClient

    init {
        client = initClient()
        retrofit = initRetrofit()
    }

    private fun initRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(CoroutineCallAdapterFactory())
            .build()
    }

    private fun initClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
                .addInterceptor(HeaderRequestInterceptor())
        }

        return builder.build()
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