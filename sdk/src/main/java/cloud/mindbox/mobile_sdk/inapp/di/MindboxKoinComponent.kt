package cloud.mindbox.mobile_sdk.inapp.di

import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.dsl.koinApplication


lateinit var koin: Koin

fun initKoin(appContext: Context) {
    koin = koinApplication {
        androidContext(appContext)
        modules(appModule, dataModule)
    }.koin
}

interface MindboxKoinComponent : KoinComponent {

    override fun getKoin(): Koin {
        return koin
    }
}