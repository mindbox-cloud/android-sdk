package cloud.mindbox.mobile_sdk.inapp.di

import cloud.mindbox.mobile_sdk.inapp.data.InAppGeoRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.data.InAppRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.data.InAppValidatorImpl
import cloud.mindbox.mobile_sdk.inapp.domain.*
import cloud.mindbox.mobile_sdk.inapp.mapper.InAppMessageMapper
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManagerImpl
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageViewDisplayerImpl
import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import cloud.mindbox.mobile_sdk.models.operation.response.PayloadDto
import cloud.mindbox.mobile_sdk.utils.RuntimeTypeAdapterFactory
import com.google.gson.GsonBuilder
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

internal val appModule = module {
    single<InAppMessageViewDisplayer> { InAppMessageViewDisplayerImpl() }
    factory<InAppMessageManager> {
        InAppMessageManagerImpl(inAppMessageViewDisplayer = get(),
            inAppInteractorImpl = get())
    }
}
internal val dataModule = module {
    factory<InAppRepository> {
        InAppRepositoryImpl(inAppMapper = get(),
            gson = get(),
            context = androidContext(),
            inAppValidator = get())
    }
    factory<InAppGeoRepository> {
        InAppGeoRepositoryImpl(context = androidContext(),
            inAppMessageMapper = get(),
            gson = get())
    }
    factory<InAppInteractor> {
        InAppInteractorImpl(inAppRepositoryImpl = get(),
            inAppGeoRepositoryImpl = get())
    }
    single<InAppValidator> { InAppValidatorImpl() }
    single { InAppMessageMapper() }
    single {
        GsonBuilder().registerTypeAdapterFactory(
            RuntimeTypeAdapterFactory.of(
                PayloadDto::class.java,
                InAppRepositoryImpl.TYPE_JSON_NAME, true
            ).registerSubtype(
                PayloadDto.SimpleImage::class.java,
                InAppRepositoryImpl.SIMPLE_IMAGE_JSON_NAME
            )
        ).registerTypeAdapterFactory(
            RuntimeTypeAdapterFactory.of(
                TreeTargetingDto::class.java,
                InAppRepositoryImpl.TYPE_JSON_NAME,
                true).registerSubtype(
                TreeTargetingDto.TrueNodeDto::class.java,
                InAppRepositoryImpl.TRUE_JSON_NAME
            ).registerSubtype(
                TreeTargetingDto.IntersectionNodeDto::class.java,
                InAppRepositoryImpl.AND_JSON_NAME
            ).registerSubtype(
                TreeTargetingDto.UnionNodeDto::class.java,
                InAppRepositoryImpl.OR_JSON_NAME
            ).registerSubtype(
                TreeTargetingDto.SegmentNodeDto::class.java,
                InAppRepositoryImpl.SEGMENT_JSON_NAME
            ).registerSubtype(
                TreeTargetingDto.CountryNodeDto::class.java,
                InAppRepositoryImpl.COUNTRY_JSON_NAME
            ).registerSubtype(
                TreeTargetingDto.CityNodeDto::class.java,
                InAppRepositoryImpl.CITY_JSON_NAME
            ).registerSubtype(
                TreeTargetingDto.RegionNodeDto::class.java,
                InAppRepositoryImpl.REGION_JSON_NAME)).create()

    }
}

