package cloud.mindbox.mobile_sdk.di.modules

import android.app.Application
import cloud.mindbox.mobile_sdk.abtests.CustomerAbMixer
import cloud.mindbox.mobile_sdk.abtests.InAppABTestLogic
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.data.managers.data_filler.*
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.inapp.data.validators.*
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppContentFetcher
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageLoader
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageSizeStorage
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.CallbackInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.*
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.*
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.validators.InAppValidator
import cloud.mindbox.mobile_sdk.inapp.presentation.*
import cloud.mindbox.mobile_sdk.managers.*
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.managers.MobileConfigSettingsManager
import cloud.mindbox.mobile_sdk.managers.RequestPermissionManager
import cloud.mindbox.mobile_sdk.managers.UserVisitManager
import cloud.mindbox.mobile_sdk.monitoring.data.mappers.MonitoringMapper
import cloud.mindbox.mobile_sdk.monitoring.data.room.MonitoringDatabase
import cloud.mindbox.mobile_sdk.monitoring.data.room.dao.MonitoringDao
import cloud.mindbox.mobile_sdk.monitoring.data.validators.MonitoringValidator
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.*
import cloud.mindbox.mobile_sdk.network.MindboxServiceGenerator
import cloud.mindbox.mobile_sdk.utils.SystemTimeProvider
import cloud.mindbox.mobile_sdk.utils.MigrationManager
import com.android.volley.RequestQueue
import com.google.gson.Gson

internal sealed interface MindboxModule

internal interface AppModule :
    AppContextModule,
    PresentationModule,
    DataModule,
    MonitoringModule,
    DomainModule,
    ApiModule {

    fun isDebug(): Boolean
}

internal interface AppContextModule : MindboxModule {
    val appContext: Application
}

internal interface PresentationModule : MindboxModule {
    val inAppMessageViewDisplayer: InAppMessageViewDisplayer
    val inAppMessageManager: InAppMessageManager
    val clipboardManager: ClipboardManager
    val activityManager: ActivityManager
}

internal interface DataModule : MindboxModule {
    val inAppContentFetcher: InAppContentFetcher
    val inAppImageSizeStorage: InAppImageSizeStorage
    val sessionStorageManager: SessionStorageManager
    val mobileConfigRepository: MobileConfigRepository
    val mobileConfigSerializationManager: MobileConfigSerializationManager
    val inAppGeoRepository: InAppGeoRepository
    val inAppRepository: InAppRepository
    val callbackRepository: CallbackRepository
    val geoSerializationManager: GeoSerializationManager
    val inAppSerializationManager: InAppSerializationManager
    val inAppSegmentationRepository: InAppSegmentationRepository
    val inAppValidator: InAppValidator
    val inAppMapper: InAppMapper
    val gson: Gson
    val monitoringValidator: MonitoringValidator
    val operationNameValidator: OperationNameValidator
    val operationValidator: OperationValidator
    val abTestValidator: ABTestValidator
    val sdkVersionValidator: SdkVersionValidator
    val jsonValidator: JsonValidator
    val xmlValidator: XmlValidator
    val urlValidator: UrlValidator
    val inAppImageLoader: InAppImageLoader
    val defaultDataManager: DataManager
    val modalWindowDtoDataFiller: ModalWindowDtoDataFiller
    val snackBarDtoDataFiller: SnackBarDtoDataFiller
    val modalElementDtoDataFiller: ModalElementDtoDataFiller
    val modalWindowValidator: ModalWindowValidator
    val imageLayerValidator: ImageLayerValidator
    val modalElementValidator: ModalElementValidator
    val snackbarValidator: SnackbarValidator
    val closeButtonModalElementValidator: CloseButtonModalElementValidator
    val closeButtonModalElementDtoDataFiller: CloseButtonModalElementDtoDataFiller
    val closeButtonPositionValidator: CloseButtonModalPositionValidator
    val closeButtonModalSizeValidator: CloseButtonModalSizeValidator
    val closeButtonModalPositionValidator: CloseButtonModalPositionValidator
    val closeButtonSnackbarElementValidator: CloseButtonSnackbarElementValidator
    val closeButtonSnackbarPositionValidator: CloseButtonSnackbarPositionValidator
    val closeButtonSnackbarSizeValidator: CloseButtonSnackbarSizeValidator
    val snackbarElementValidator: SnackBarElementValidator
    val snackBarElementDtoDataFiller: SnackbarElementDtoDataFiller
    val closeButtonSnackbarElementDtoDataFiller: CloseButtonSnackbarElementDtoDataFiller
    val mindboxNotificationManager: MindboxNotificationManager
    val permissionManager: PermissionManager
    val requestPermissionManager: RequestPermissionManager
    val ttlParametersValidator: TtlParametersValidator
    val inAppConfigTtlValidator: InAppConfigTtlValidator
    val frequencyDataFiller: FrequencyDataFiller
    val frequencyValidator: FrequencyValidator
    val migrationManager: MigrationManager
    val timeProvider: SystemTimeProvider
    val slidingExpirationParametersValidator: TimeSpanPositiveValidator
    val mobileConfigSettingsManager: MobileConfigSettingsManager
    val integerPositiveValidator: IntegerPositiveValidator
    val inappSettingsManager: InappSettingsManager
}

internal interface MonitoringModule : MindboxModule {
    val monitoringMapper: MonitoringMapper
    val monitoringRepository: MonitoringRepository
    val logResponseDataManager: LogResponseDataManager
    val logRequestDataManager: LogRequestDataManager
    val logStoringDataChecker: LogStoringDataChecker
    val monitoringInteractor: MonitoringInteractor
    val monitoringDatabase: MonitoringDatabase
    val monitoringDao: MonitoringDao
}

internal interface DomainModule : MindboxModule {
    val inAppInteractor: InAppInteractor
    val callbackInteractor: CallbackInteractor
    val inAppProcessingManager: InAppProcessingManager
    val inAppEventManager: InAppEventManager
    val inAppFilteringManager: InAppFilteringManager
    val customerAbMixer: CustomerAbMixer
    val inAppABTestLogic: InAppABTestLogic
    val userVisitManager: UserVisitManager
    val inAppFrequencyManager: InAppFrequencyManager
}

internal interface ApiModule : MindboxModule {
    val gatewayManager: GatewayManager
    val mindboxServiceGenerator: MindboxServiceGenerator
    val requestQueue: RequestQueue
}
