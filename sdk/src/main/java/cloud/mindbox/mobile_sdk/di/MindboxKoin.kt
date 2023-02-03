package cloud.mindbox.mobile_sdk.di

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.dsl.koinApplication

internal object MindboxKoin {
    lateinit var koin: Koin
        private set

    fun isInitialized() = MindboxKoin::koin.isInitialized

    fun init(appContext: Context) {
        if (isInitialized()) return
        koin = koinApplication {
            androidContext(appContext)
            modules(appModule, dataModule, monitoringModule)
        }.koin
    }

    /**
     * Must be used only with internal classes
     * For public classes use MindboxKoin.koin
     */
    interface MindboxKoinComponent : KoinComponent {
        override fun getKoin(): Koin {
            return koin
        }
    }
}