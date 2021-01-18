package cloud.mindbox.mobile_sdk.network

import android.content.Context
import android.os.Build
import cloud.mindbox.mobile_sdk.BuildConfig
import com.android.volley.RequestQueue
import com.android.volley.VolleyLog
import com.android.volley.toolbox.Volley
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// todo
// прикрутить свое логирование запорсов
// добавить кастомный класс для запросов
// построение запросов и отправка

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

    internal fun <T> addToRequestQueue(req: com.android.volley.Request<T>) {
        requestQueue.add(req)
    }
}

internal object ServiceGeneratorOld {

    private const val BASE_URL_PLACEHOLDER = "https://%s/"

    fun initRetrofit(
        domain: String,
        packageName: String,
        versionName: String,
        versionCode: String
    ): Retrofit {
        val client: OkHttpClient = initClient(packageName, versionName, versionCode)

        return Retrofit.Builder()
            .baseUrl(String.format(BASE_URL_PLACEHOLDER, domain))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun initClient(
        packageName: String,
        versionName: String,
        versionCode: String
    ): OkHttpClient {
        return OkHttpClient.Builder().apply {

            addInterceptor(HeaderRequestInterceptor(packageName, versionName, versionCode))

            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
            }
        }
            .build()
    }

    internal class HeaderRequestInterceptor(
        private val packageName: String,
        private val versionName: String,
        private val versionCode: String
    ) : Interceptor {

        companion object {
            private const val HEADER_CONTENT_TYPE = "Content-Type"
            private const val HEADER_USER_AGENT = "User-Agent"
            private const val HEADER_INTEGRATION = "Mindbox-Integration"
            private const val HEADER_INTEGRATION_VERSION = "Mindbox-Integration-Version"

            private const val VALUE_CONTENT_TYPE = "application/json; charset=utf-8"
            private const val VALUE_USER_AGENT =
                "%1$1s + %2$1s(%3$1s), android + %4$1s, %5$1s, %6$1s" // format: {host.application.name + app_version(version_code), os + version, vendor, model}
            private const val VALUE_INTEGRATION = "Android-SDK"
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val newRequest: Request
            newRequest = request.newBuilder()
                .header(HEADER_CONTENT_TYPE, VALUE_CONTENT_TYPE)
                .header(
                    HEADER_USER_AGENT,
                    String.format(
                        VALUE_USER_AGENT,
                        packageName,
                        versionName,
                        versionCode,
                        Build.VERSION.RELEASE,
                        Build.MANUFACTURER,
                        Build.MODEL
                    )
                )
                .header(HEADER_INTEGRATION, VALUE_INTEGRATION)
                .header(HEADER_INTEGRATION_VERSION, BuildConfig.VERSION_NAME)
                .build()
            return chain.proceed(newRequest)
        }
    }
}