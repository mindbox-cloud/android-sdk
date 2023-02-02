package cloud.mindbox.mobile_sdk.di

import androidx.room.Room
import cloud.mindbox.mobile_sdk.inapp.data.InAppGeoRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.data.InAppRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.data.InAppValidatorImpl
import cloud.mindbox.mobile_sdk.inapp.domain.*
import cloud.mindbox.mobile_sdk.inapp.mapper.InAppMessageMapper
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManagerImpl
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageViewDisplayerImpl
import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import cloud.mindbox.mobile_sdk.models.operation.response.PayloadDto
import cloud.mindbox.mobile_sdk.monitoring.MonitoringInteractorImpl
import cloud.mindbox.mobile_sdk.monitoring.data.checkers.LogStoringDataCheckerImpl
import cloud.mindbox.mobile_sdk.monitoring.data.repositories.MonitoringRepositoryImpl
import cloud.mindbox.mobile_sdk.monitoring.data.rmappers.MonitoringMapper
import cloud.mindbox.mobile_sdk.monitoring.data.room.MonitoringDatabase
import cloud.mindbox.mobile_sdk.monitoring.data.validators.MonitoringValidator
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.*
import cloud.mindbox.mobile_sdk.monitoring.domain.managers.LogRequestDataManagerImpl
import cloud.mindbox.mobile_sdk.monitoring.domain.managers.LogResponseDataManagerImpl
import cloud.mindbox.mobile_sdk.utils.RuntimeTypeAdapterFactory
import com.google.gson.GsonBuilder
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File

internal const val monitoringDatabaseName = "MonitoringDatabase"

internal val monitoringModule = module {
    single { MonitoringMapper() }
    single<MonitoringRepository> {
        MonitoringRepositoryImpl(
            monitoringDao = get(),
            monitoringMapper = get(),
            context = get(),
            gson = get(),
            logStoringDataChecker = get()
        )
    }
    single { MonitoringValidator() }
    single<LogResponseDataManager> { LogResponseDataManagerImpl() }
    single<LogRequestDataManager> { LogRequestDataManagerImpl() }
    single<LogStoringDataChecker> {
        LogStoringDataCheckerImpl(
            File(
                "${
                    androidContext().filesDir.absolutePath.replace(
                        "files",
                        "databases"
                    )
                }/$monitoringDatabaseName"
            )
        )
    }
    single<MonitoringInteractor> {
        MonitoringInteractorImpl(
            inAppRepository = get(),
            monitoringRepository = get(),
            logResponseDataManager = get(),
            logRequestDataManager = get()
        )
    }
    factory {
        Room.databaseBuilder(
            androidContext(),
            MonitoringDatabase::class.java,
            monitoringDatabaseName
        ).fallbackToDestructiveMigration().build()
    }
    single { get<MonitoringDatabase>().monitoringDao() }
}
internal val appModule = module {
    single<InAppMessageViewDisplayer> { InAppMessageViewDisplayerImpl() }
    factory<InAppMessageManager> {
        InAppMessageManagerImpl(
            inAppMessageViewDisplayer = get(),
            inAppInteractorImpl = get(), monitoringRepository = get()
        )
    }
}
internal val dataModule = module {
    single<InAppRepository> {
        InAppRepositoryImpl(
            inAppMapper = get(),
            gson = get(),
            context = androidContext(),
            inAppValidator = get(),
            monitoringValidator = get()
        )
    }
    factory<InAppGeoRepository> {
        InAppGeoRepositoryImpl(
            context = androidContext(),
            inAppMessageMapper = get(),
            gson = get()
        )
    }
    factory<InAppInteractor> {
        InAppInteractorImpl(
            inAppRepositoryImpl = get(),
            inAppGeoRepositoryImpl = get()
        )
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
                true
            ).registerSubtype(
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
                InAppRepositoryImpl.REGION_JSON_NAME
            )
        ).create()
    }
}

