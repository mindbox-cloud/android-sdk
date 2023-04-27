package cloud.mindbox.mobile_sdk.di

import androidx.room.Room
import cloud.mindbox.mobile_sdk.R
import cloud.mindbox.mobile_sdk.inapp.data.managers.GeoSerializationManagerImpl
import cloud.mindbox.mobile_sdk.inapp.data.managers.InAppSerializationManagerImpl
import cloud.mindbox.mobile_sdk.inapp.data.managers.MobileConfigSerializationManagerImpl
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.inapp.data.repositories.InAppGeoRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.data.repositories.InAppRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.data.repositories.InAppSegmentationRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.data.repositories.MobileConfigRepositoryImpl
import cloud.mindbox.mobile_sdk.inapp.data.validators.InAppValidatorImpl
import cloud.mindbox.mobile_sdk.inapp.data.validators.OperationNameValidator
import cloud.mindbox.mobile_sdk.inapp.data.validators.OperationValidator
import cloud.mindbox.mobile_sdk.inapp.domain.InAppChoosingManagerImpl
import cloud.mindbox.mobile_sdk.inapp.domain.InAppContentFetcherImpl
import cloud.mindbox.mobile_sdk.inapp.domain.InAppEventManagerImpl
import cloud.mindbox.mobile_sdk.inapp.domain.InAppFilteringManagerImpl
import cloud.mindbox.mobile_sdk.inapp.domain.InAppInteractorImpl
import cloud.mindbox.mobile_sdk.inapp.domain.InAppPicassoImageLoaderImpl
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppContentFetcher
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageLoader
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
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.LogRequestDataManager
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.LogResponseDataManager
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.LogStoringDataChecker
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.MonitoringInteractor
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.MonitoringRepository
import cloud.mindbox.mobile_sdk.monitoring.domain.managers.LogRequestDataManagerImpl
import cloud.mindbox.mobile_sdk.monitoring.domain.managers.LogResponseDataManagerImpl
import cloud.mindbox.mobile_sdk.utils.RuntimeTypeAdapterFactory
import com.google.gson.GsonBuilder
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.TimeUnit

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

    factory { OperationNameValidator() }
    factory { OperationValidator() }
}
internal val presentationModule = module {
    single<InAppMessageViewDisplayer> { InAppMessageViewDisplayerImpl(get()) }
    single<InAppMessageManager> {
        InAppMessageManagerImpl(
            inAppMessageViewDisplayer = get(),
            inAppInteractorImpl = get(),
            defaultDispatcher = Dispatchers.IO,
            monitoringRepository = get()
        )
    }
    single<Picasso> {
        Picasso.Builder(get()).downloader(
            OkHttp3Downloader(
                OkHttpClient.Builder()
                    .writeTimeout(0, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.SECONDS)
                    .connectTimeout(0, TimeUnit.SECONDS)
                    .callTimeout(
                    androidContext().getString(R.string.mindbox_inapp_fetching_timeout).toLong(),
                    TimeUnit.SECONDS
                ).build()
            )
        ).build()
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
            inAppGeoRepository = get(),
            inAppSegmentationRepository = get(),
            inAppContentFetcher = get(),
            inAppRepository = get(),
        )
    }
    factory<InAppContentFetcher> {
        InAppContentFetcherImpl(inAppImageLoader = get())
    }
    factory<InAppImageLoader> { InAppPicassoImageLoaderImpl(get()) }
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
            monitoringValidator = get(),
            operationNameValidator = get(),
            operationValidator = get(),
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
            ).registerSubtype(
                TreeTargetingDto.ViewProductCategoryNodeDto::class.java,
                TreeTargetingDto.ViewProductCategoryNodeDto.VIEW_PRODUCT_CATEGORY_ID_JSON_NAME
            ).registerSubtype(
                TreeTargetingDto.ViewProductCategoryInNodeDto::class.java,
                TreeTargetingDto.ViewProductCategoryInNodeDto.VIEW_PRODUCT_CATEGORY_ID_IN_JSON_NAME
            ).registerSubtype(
                TreeTargetingDto.ViewProductSegmentNodeDto::class.java,
                TreeTargetingDto.ViewProductSegmentNodeDto.VIEW_PRODUCT_SEGMENT_JSON_NAME
            ).registerSubtype(
                TreeTargetingDto.ViewProductNodeDto::class.java,
                TreeTargetingDto.ViewProductNodeDto.VIEW_PRODUCT_ID_JSON_NAME
            )
        ).create()
    }
}

