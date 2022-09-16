package cloud.mindbox.mobile_sdk.inapp.di

import cloud.mindbox.mobile_sdk.inapp.data.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManager
import org.koin.dsl.module

internal val appModule = module {
    single { InAppMessageManager() }
}
internal val dataModule = module {
    single { InAppRepository() }
    single { InAppInteractor() }
}
