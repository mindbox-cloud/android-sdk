package cloud.mindbox.mobile_sdk.inapp.di

import cloud.mindbox.mobile_sdk.inapp.data.InAppRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.domain.InAppInteractorImpl
import cloud.mindbox.mobile_sdk.inapp.mapper.InAppMessageMapper
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManager
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageViewDisplayerImpl
import com.google.gson.Gson
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

internal val appModule = module {
    single { InAppMessageViewDisplayerImpl() }
    single { InAppMessageManager() }
    single { androidContext() }
}
internal val dataModule = module {
    single { InAppRepositoryImpl() }
    single { InAppInteractorImpl() }
    single { InAppMessageMapper() }
    single { Gson() }
}

