package cloud.mindbox.mobile_sdk.di

import androidx.room.Room
import cloud.mindbox.mobile_sdk.inapp.data.InAppValidatorImpl
import cloud.mindbox.mobile_sdk.inapp.data.managers.GeoSerializationManagerImpl
import cloud.mindbox.mobile_sdk.inapp.data.managers.InAppSerializationManagerImpl
import cloud.mindbox.mobile_sdk.inapp.data.managers.MobileConfigSerializationManagerImpl
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.inapp.data.repositories.InAppGeoRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.data.repositories.InAppRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.data.repositories.InAppSegmentationRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.data.repositories.MobileConfigRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.domain.InAppChoosingManagerImpl
import cloud.mindbox.mobile_sdk.inapp.domain.InAppEventManagerImpl
import cloud.mindbox.mobile_sdk.inapp.domain.InAppFilteringManagerImpl
import cloud.mindbox.mobile_sdk.inapp.domain.InAppInteractorImpl
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.GeoSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppChoosingManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppEventManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFilteringManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.MobileConfigSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppGeoRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.validators.InAppValidator
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManager
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManagerImpl
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageViewDisplayer
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageViewDisplayerImpl
import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import cloud.mindbox.mobile_sdk.models.operation.response.PayloadDto
import cloud.mindbox.mobile_sdk.monitoring.MonitoringInteractorImpl
import cloud.mindbox.mobile_sdk.monitoring.data.checkers.LogStoringDataCheckerImpl
import cloud.mindbox.mobile_sdk.monitoring.data.mappers.MonitoringMapper
import cloud.mindbox.mobile_sdk.monitoring.data.repositories.MonitoringRepositoryImpl
import cloud.mindbox.mobile_sdk.monitoring.data.room.MonitoringDatabase
import cloud.mindbox.mobile_sdk.monitoring.data.validators.MonitoringValidator
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.*
import cloud.mindbox.mobile_sdk.monitoring.domain.managers.LogRequestDataManagerImpl
import cloud.mindbox.mobile_sdk.monitoring.domain.managers.LogResponseDataManagerImpl
import cloud.mindbox.mobile_sdk.utils.RuntimeTypeAdapterFactory
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
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
            logStoringDataChecker = get(),
            monitoringValidator = get()
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
            mobileConfigRepository = get(),
            monitoringRepository = get(),
            logResponseDataManager = get(),
            logRequestDataManager = get()
        )
    }
    single {
        Room.databaseBuilder(
            androidContext(),
            MonitoringDatabase::class.java,
            monitoringDatabaseName
        )
            .fallbackToDestructiveMigration()
            .addMigrations(MonitoringDatabase.MIGRATION_1_2)
            .build()
    }
    single { get<MonitoringDatabase>().monitoringDao() }
}
internal val presentationModule = module {
    single<InAppMessageViewDisplayer> { InAppMessageViewDisplayerImpl() }
    single<InAppMessageManager> {
        InAppMessageManagerImpl(
            inAppMessageViewDisplayer = get(),
            inAppInteractorImpl = get(),
            defaultDispatcher = Dispatchers.IO,
            monitoringRepository = get()
        )
    }
}

internal val domainModule = module {
    single<InAppInteractor> {
        InAppInteractorImpl(
            mobileConfigRepository = get(),
            inAppRepository = get(),
            inAppSegmentationRepository = get(),
            inAppFilteringManager = get(),
            inAppEventManager = get(),
            inAppChoosingManager = get()
        )
    }
    single<InAppChoosingManager> {
        InAppChoosingManagerImpl(
            inAppGeoRepository = get(), inAppSegmentationRepository = get(),
            inAppFilteringManager =
            get()
        )
    }
    factory<InAppEventManager> {
        InAppEventManagerImpl()
    }
    factory<InAppFilteringManager> {
        InAppFilteringManagerImpl(inAppRepository = get())
    }
}
internal val dataModule = module {
    single {
        SessionStorageManager()
    }
    single<MobileConfigRepository> {
        MobileConfigRepositoryImpl(
            inAppMapper = get(),
            mobileConfigSerializationManager = get(),
            context = androidContext(),
            inAppValidator = get(),
            monitoringValidator = get()
        )
    }
    factory<MobileConfigSerializationManager> {
        MobileConfigSerializationManagerImpl(
            gson = get()
        )
    }
    single<InAppGeoRepository> {
        InAppGeoRepositoryImpl(
            context = androidContext(),
            inAppMapper = get(),
            geoSerializationManager = get(),
            sessionStorageManager = get()
        )
    }
    single<InAppRepository> {
        InAppRepositoryImpl(
            context = androidContext(),
            sessionStorageManager = get(),
            inAppSerializationManager = get()
        )
    }
    factory<GeoSerializationManager> {
        GeoSerializationManagerImpl(gson = get())
    }
    factory<InAppSerializationManager> {
        InAppSerializationManagerImpl(
            gson = get()
        )
    }
    single<InAppSegmentationRepository> {
        InAppSegmentationRepositoryImpl(
            context = androidContext(),
            inAppMapper = get(),
            sessionStorageManager = get()
        )
    }
    single<InAppValidator> { InAppValidatorImpl() }
    single { InAppMapper() }
    single {
        GsonBuilder().registerTypeAdapterFactory(
            RuntimeTypeAdapterFactory.of(
                PayloadDto::class.java,
                TreeTargetingDto.TYPE_JSON_NAME, true
            ).registerSubtype(
                PayloadDto.SimpleImage::class.java,
                PayloadDto.SimpleImage.SIMPLE_IMAGE_JSON_NAME
            )
        ).registerTypeAdapterFactory(
            RuntimeTypeAdapterFactory.of(
                TreeTargetingDto::class.java,
                TreeTargetingDto.TYPE_JSON_NAME,
                true
            ).registerSubtype(
                TreeTargetingDto.TrueNodeDto::class.java,
                TreeTargetingDto.TrueNodeDto.TRUE_JSON_NAME
            ).registerSubtype(
                TreeTargetingDto.IntersectionNodeDto::class.java,
                TreeTargetingDto.IntersectionNodeDto.AND_JSON_NAME
            ).registerSubtype(
                TreeTargetingDto.UnionNodeDto::class.java,
                TreeTargetingDto.UnionNodeDto.OR_JSON_NAME
            ).registerSubtype(
                TreeTargetingDto.SegmentNodeDto::class.java,
                TreeTargetingDto.SegmentNodeDto.SEGMENT_JSON_NAME
            ).registerSubtype(
                TreeTargetingDto.CountryNodeDto::class.java,
                TreeTargetingDto.CountryNodeDto.COUNTRY_JSON_NAME
            ).registerSubtype(
                TreeTargetingDto.CityNodeDto::class.java,
                TreeTargetingDto.CityNodeDto.CITY_JSON_NAME
            ).registerSubtype(
                TreeTargetingDto.RegionNodeDto::class.java,
                TreeTargetingDto.RegionNodeDto.REGION_JSON_NAME
            ).registerSubtype(
                TreeTargetingDto.OperationNodeDto::class.java,
                TreeTargetingDto.OperationNodeDto.API_METHOD_CALL_JSON_NAME
            )
        ).create()
    }
}

