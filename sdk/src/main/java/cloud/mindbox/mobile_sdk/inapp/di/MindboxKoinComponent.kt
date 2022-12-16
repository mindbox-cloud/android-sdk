package cloud.mindbox.mobile_sdk.inapp.di

import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.dsl.koinApplication

private lateinit var appContext: Context

fun initKoin(context: Context) {
    appContext = context
}

val koin: Koin by lazy {
    koinApplication {
        androidContext(appContext)
        modules(appModule, dataModule)
    }.koin
}

interface MindboxKoinComponent : KoinComponent {

    override fun getKoin(): Koin {
        return koin
    }
}