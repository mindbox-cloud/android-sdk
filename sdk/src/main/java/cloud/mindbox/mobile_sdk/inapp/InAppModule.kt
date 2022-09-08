package cloud.mindbox.mobile_sdk.inapp

import org.koin.dsl.module

val appModule = module {
    single { InAppMessageManager() }
}
