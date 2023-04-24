package cloud.mindbox.mobile_sdk.di

import cloud.mindbox.mobile_sdk.di.modules.ApiModule
import cloud.mindbox.mobile_sdk.di.modules.AppContextModule
import cloud.mindbox.mobile_sdk.di.modules.DataModule
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
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.GeoSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.MobileConfigSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppGeoRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.validators.InAppValidator
import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import cloud.mindbox.mobile_sdk.models.operation.response.PayloadDto
import cloud.mindbox.mobile_sdk.monitoring.data.validators.MonitoringValidator
import cloud.mindbox.mobile_sdk.utils.RuntimeTypeAdapterFactory
import com.google.gson.Gson
import com.google.gson.GsonBuilder


internal fun DataModule(
    appContextModule: AppContextModule,
    apiModule: ApiModule
): DataModule = object : DataModule {

    override val sessionStorageManager: SessionStorageManager by lazy { SessionStorageManager() }

    override val mobileConfigRepository: MobileConfigRepository by lazy {
        MobileConfigRepositoryImpl(
            inAppMapper = inAppMapper,
            mobileConfigSerializationManager = mobileConfigSerializationManager,
            inAppValidator = inAppValidator,
            monitoringValidator = monitoringValidator,
            operationNameValidator = operationNameValidator,
            operationValidator = operationValidator,
            gatewayManager = apiModule.gatewayManager,
        )
    }

    override val mobileConfigSerializationManager: MobileConfigSerializationManager
        get() = MobileConfigSerializationManagerImpl(gson = gson)

    override val inAppGeoRepository: InAppGeoRepository by lazy {
        InAppGeoRepositoryImpl(
            context = appContextModule.appContext,
            inAppMapper = inAppMapper,
            geoSerializationManager = geoSerializationManager,
            sessionStorageManager = sessionStorageManager,
            gatewayManager = apiModule.gatewayManager,
        )
    }

    override val inAppRepository: InAppRepository by lazy {
        InAppRepositoryImpl(
            context = appContextModule.appContext,
            sessionStorageManager = sessionStorageManager,
            inAppSerializationManager = inAppSerializationManager,
        )
    }

    override val geoSerializationManager: GeoSerializationManager
        get() = GeoSerializationManagerImpl(gson = gson)

    override val inAppSerializationManager: InAppSerializationManager
        get() = InAppSerializationManagerImpl(gson = gson)

    override val inAppSegmentationRepository: InAppSegmentationRepository by lazy {
        InAppSegmentationRepositoryImpl(
            context = appContextModule.appContext,
            inAppMapper = inAppMapper,
            sessionStorageManager = sessionStorageManager,
            gatewayManager = apiModule.gatewayManager,
        )
    }

    override val monitoringValidator: MonitoringValidator by lazy { MonitoringValidator() }

    override val inAppValidator: InAppValidator by lazy { InAppValidatorImpl() }

    override val operationNameValidator: OperationNameValidator
        get() = OperationNameValidator()

    override val operationValidator: OperationValidator
        get() = OperationValidator()

    override val inAppMapper: InAppMapper by lazy { InAppMapper() }

    override val gson: Gson by lazy {
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