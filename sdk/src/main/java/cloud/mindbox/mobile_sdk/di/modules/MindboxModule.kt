package cloud.mindbox.mobile_sdk.di.modules

import android.app.Application
import cloud.mindbox.mobile_sdk.abtests.CustomerAbMixer
import cloud.mindbox.mobile_sdk.abtests.InAppABTestLogic
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.inapp.data.validators.ABTestValidator
import cloud.mindbox.mobile_sdk.inapp.data.validators.OperationNameValidator
import cloud.mindbox.mobile_sdk.inapp.data.validators.OperationValidator
import cloud.mindbox.mobile_sdk.inapp.data.validators.SdkVersionValidator
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppContentFetcher
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageLoader
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.*
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppGeoRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.validators.InAppValidator
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManager
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageViewDisplayer
import cloud.mindbox.mobile_sdk.managers.GatewayManager
import cloud.mindbox.mobile_sdk.monitoring.data.mappers.MonitoringMapper
import cloud.mindbox.mobile_sdk.monitoring.data.room.MonitoringDatabase
import cloud.mindbox.mobile_sdk.monitoring.data.room.dao.MonitoringDao
import cloud.mindbox.mobile_sdk.monitoring.data.validators.MonitoringValidator
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.*
import cloud.mindbox.mobile_sdk.network.MindboxServiceGenerator
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
}

internal interface DataModule : MindboxModule {
    val sessionStorageManager: SessionStorageManager
    val mobileConfigRepository: MobileConfigRepository
    val mobileConfigSerializationManager: MobileConfigSerializationManager
    val inAppGeoRepository: InAppGeoRepository
    val inAppRepository: InAppRepository
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
    val inAppChoosingManager: InAppChoosingManager
    val inAppEventManager: InAppEventManager
    val inAppFilteringManager: InAppFilteringManager
    val customerAbMixer: CustomerAbMixer
    val inAppABTestLogic: InAppABTestLogic
}

internal interface ApiModule : MindboxModule {
    val gatewayManager: GatewayManager
    val mindboxServiceGenerator: MindboxServiceGenerator
    val requestQueue: RequestQueue
    val inAppContentFetcher: InAppContentFetcher
    val inAppImageLoader: InAppImageLoader
}
