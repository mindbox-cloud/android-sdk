package cloud.mindbox.mobile_sdk.inapp.di

import cloud.mindbox.mobile_sdk.inapp.data.InAppRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.domain.*
import cloud.mindbox.mobile_sdk.inapp.mapper.InAppMessageMapper
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManagerImpl
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageViewDisplayerImpl
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

internal val appModule = module {
    single<InAppMessageViewDisplayer> { InAppMessageViewDisplayerImpl() }
    factory<InAppMessageManager> { InAppMessageManagerImpl(get(), get()) }
}
internal val dataModule = module {
    factory<InAppRepository> { InAppRepositoryImpl(get(), get(), androidContext()) }
    factory<InAppInteractor> { InAppInteractorImpl(get()) }
    single { InAppMessageMapper() }
    single { Gson() }
}

