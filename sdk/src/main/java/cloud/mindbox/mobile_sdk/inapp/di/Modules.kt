package cloud.mindbox.mobile_sdk.inapp.di

import cloud.mindbox.mobile_sdk.inapp.data.InAppRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.domain.*
import cloud.mindbox.mobile_sdk.inapp.mapper.InAppMessageMapper
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManagerImpl
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageViewDisplayerImpl
import cloud.mindbox.mobile_sdk.models.operation.response.PayloadDto
import cloud.mindbox.mobile_sdk.utils.RuntimeTypeAdapterFactory
import com.google.gson.GsonBuilder
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.dsl.module


internal val appModule = module {
    single<InAppMessageViewDisplayer> { InAppMessageViewDisplayerImpl() }
    factory<InAppMessageManager> { InAppMessageManagerImpl(get(), get()) }
}
internal val dataModule = module {
    factory<InAppRepository> { InAppRepositoryImpl(get(), get(), androidContext()) }
    factory<InAppInteractor> { InAppInteractorImpl(get()) }
    single { InAppMessageMapper() }
    single {
        GsonBuilder().registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(
            PayloadDto::class.java,
            InAppRepositoryImpl.TYPE_JSON_NAME, true)
            .registerSubtype(PayloadDto.SimpleImage::class.java,
                InAppRepositoryImpl.SIMPLE_IMAGE_JSON_NAME))
            .create()
    }
}

