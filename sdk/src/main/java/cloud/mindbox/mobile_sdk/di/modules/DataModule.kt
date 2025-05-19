package cloud.mindbox.mobile_sdk.di.modules

import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadBlankDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.inapp.data.managers.*
import cloud.mindbox.mobile_sdk.inapp.data.managers.data_filler.*
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.inapp.data.repositories.*
import cloud.mindbox.mobile_sdk.inapp.data.validators.*
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppContentFetcher
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageLoader
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageSizeStorage
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.GeoSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.MobileConfigSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.*
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.validators.InAppValidator
import cloud.mindbox.mobile_sdk.inapp.presentation.MindboxNotificationManager
import cloud.mindbox.mobile_sdk.inapp.presentation.MindboxNotificationManagerImpl
import cloud.mindbox.mobile_sdk.managers.MobileConfigSettingsManagerImpl
import cloud.mindbox.mobile_sdk.managers.RequestPermissionManager
import cloud.mindbox.mobile_sdk.managers.RequestPermissionManagerImpl
import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import cloud.mindbox.mobile_sdk.models.operation.response.FrequencyDto
import cloud.mindbox.mobile_sdk.monitoring.data.validators.MonitoringValidator
import cloud.mindbox.mobile_sdk.utils.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder

internal fun DataModule(
    appContextModule: AppContextModule,
    apiModule: ApiModule
): DataModule = object : DataModule,
    AppContextModule by appContextModule,
    ApiModule by apiModule {

    override val inAppImageLoader: InAppImageLoader
        get() = InAppGlideImageLoaderImpl(
            appContext,
            inAppImageSizeStorage
        )

    override val modalElementDtoDataFiller: ModalElementDtoDataFiller
        get() = ModalElementDtoDataFiller(closeButtonModalElementDtoDataFiller = closeButtonModalElementDtoDataFiller)

    override val modalWindowValidator: ModalWindowValidator by lazy {
        ModalWindowValidator(
            imageLayerValidator = imageLayerValidator,
            elementValidator = modalElementValidator
        )
    }
    override val imageLayerValidator: ImageLayerValidator
        get() = ImageLayerValidator()

    override val modalElementValidator: ModalElementValidator by lazy {
        ModalElementValidator(
            closeButtonElementValidator = closeButtonModalElementValidator
        )
    }

    override val snackbarValidator: SnackbarValidator by lazy {
        SnackbarValidator(
            imageLayerValidator,
            snackbarElementValidator
        )
    }
    override val closeButtonModalElementValidator: CloseButtonModalElementValidator
        get() = CloseButtonModalElementValidator(
            sizeValidator = CloseButtonModalSizeValidator(),
            positionValidator = CloseButtonModalPositionValidator()
        )
    override val closeButtonModalPositionValidator: CloseButtonModalPositionValidator
        get() = CloseButtonModalPositionValidator()
    override val closeButtonSnackbarElementValidator: CloseButtonSnackbarElementValidator
        get() = CloseButtonSnackbarElementValidator(
            positionValidator = closeButtonSnackbarPositionValidator,
            sizeValidator = closeButtonSnackbarSizeValidator
        )
    override val closeButtonSnackbarPositionValidator: CloseButtonSnackbarPositionValidator
        get() = CloseButtonSnackbarPositionValidator()
    override val closeButtonSnackbarSizeValidator: CloseButtonSnackbarSizeValidator
        get() = CloseButtonSnackbarSizeValidator()
    override val closeButtonModalElementDtoDataFiller: CloseButtonModalElementDtoDataFiller
        get() = CloseButtonModalElementDtoDataFiller()
    override val closeButtonPositionValidator: CloseButtonModalPositionValidator
        get() = CloseButtonModalPositionValidator()
    override val closeButtonModalSizeValidator: CloseButtonModalSizeValidator
        get() = CloseButtonModalSizeValidator()

    override val snackbarElementValidator: SnackBarElementValidator by lazy {
        SnackBarElementValidator(
            closeButtonElementValidator = CloseButtonSnackbarElementValidator(
                positionValidator = CloseButtonSnackbarPositionValidator(),
                sizeValidator = CloseButtonSnackbarSizeValidator()
            )
        )
    }
    override val snackBarElementDtoDataFiller: SnackbarElementDtoDataFiller
        get() = SnackbarElementDtoDataFiller(closeButtonSnackbarElementDtoDataFiller = closeButtonSnackbarElementDtoDataFiller)
    override val closeButtonSnackbarElementDtoDataFiller: CloseButtonSnackbarElementDtoDataFiller
        get() = CloseButtonSnackbarElementDtoDataFiller()

    override val modalWindowDtoDataFiller: ModalWindowDtoDataFiller
        by lazy { ModalWindowDtoDataFiller(elementDtoDataFiller = modalElementDtoDataFiller) }

    override val snackBarDtoDataFiller: SnackBarDtoDataFiller
        by lazy { SnackBarDtoDataFiller(elementDtoDataFiller = snackBarElementDtoDataFiller) }

    override val defaultDataManager: DataManager by lazy {
        DataManager(
            modalWindowDtoDataFiller = modalWindowDtoDataFiller,
            snackBarDtoDataFiller = snackBarDtoDataFiller,
            frequencyDataFiller = frequencyDataFiller
        )
    }

    override val inAppImageSizeStorage: InAppImageSizeStorage by lazy { InAppImageSizeStorageImpl() }

    override val sessionStorageManager: SessionStorageManager by lazy { SessionStorageManager(timeProvider) }

    override val permissionManager: PermissionManager
        get() = PermissionManagerImpl(appContext)

    override val inAppContentFetcher: InAppContentFetcher by lazy {
        InAppContentFetcherImpl(
            inAppImageLoader
        )
    }

    override val mobileConfigRepository: MobileConfigRepository by lazy {
        MobileConfigRepositoryImpl(
            inAppMapper = inAppMapper,
            mobileConfigSerializationManager = mobileConfigSerializationManager,
            inAppValidator = inAppValidator,
            abTestValidator = abTestValidator,
            monitoringValidator = monitoringValidator,
            operationNameValidator = operationNameValidator,
            operationValidator = operationValidator,
            gatewayManager = gatewayManager,
            defaultDataManager = defaultDataManager,
            ttlParametersValidator = ttlParametersValidator,
            inAppConfigTtlValidator = inAppConfigTtlValidator,
            sessionStorageManager = sessionStorageManager,
            timeSpanPositiveValidator = slidingExpirationParametersValidator,
            mobileConfigSettingsManager = mobileConfigSettingsManager
        )
    }

    override val mobileConfigSerializationManager: MobileConfigSerializationManager
        get() = MobileConfigSerializationManagerImpl(
            gson = gson.newBuilder()
                .registerTypeAdapter(String::class.java, StrictStringAdapter())
                .create()
        )

    override val inAppGeoRepository: InAppGeoRepository by lazy {
        InAppGeoRepositoryImpl(
            context = appContext,
            inAppMapper = inAppMapper,
            geoSerializationManager = geoSerializationManager,
            sessionStorageManager = sessionStorageManager,
            gatewayManager = gatewayManager,
        )
    }

    override val inAppRepository: InAppRepository by lazy {
        InAppRepositoryImpl(
            context = appContext,
            sessionStorageManager = sessionStorageManager,
            inAppSerializationManager = inAppSerializationManager,
        )
    }
    override val callbackRepository: CallbackRepository by lazy {
        CallbackRepositoryImpl(
            xmlValidator = xmlValidator,
            jsonValidator = jsonValidator,
            urlValidator = urlValidator
        )
    }

    override val geoSerializationManager: GeoSerializationManager
        get() = GeoSerializationManagerImpl(gson = gson)

    override val inAppSerializationManager: InAppSerializationManager
        get() = InAppSerializationManagerImpl(gson = gson)

    override val inAppSegmentationRepository: InAppSegmentationRepository by lazy {
        InAppSegmentationRepositoryImpl(
            inAppMapper = inAppMapper,
            sessionStorageManager = sessionStorageManager,
            gatewayManager = gatewayManager,
        )
    }

    override val monitoringValidator: MonitoringValidator by lazy { MonitoringValidator() }

    override val inAppValidator: InAppValidator by lazy {
        InAppValidatorImpl(
            sdkVersionValidator = sdkVersionValidator,
            modalWindowValidator = modalWindowValidator,
            snackbarValidator = snackbarValidator,
            frequencyValidator = frequencyValidator
        )
    }

    override val abTestValidator: ABTestValidator by lazy { ABTestValidator(sdkVersionValidator) }

    override val sdkVersionValidator: SdkVersionValidator by lazy { SdkVersionValidator() }
    override val jsonValidator: JsonValidator by lazy { JsonValidator() }
    override val xmlValidator: XmlValidator by lazy { XmlValidator() }
    override val urlValidator: UrlValidator by lazy { UrlValidator() }

    override val operationNameValidator: OperationNameValidator
        get() = OperationNameValidator()

    override val operationValidator: OperationValidator
        get() = OperationValidator()

    override val ttlParametersValidator: TtlParametersValidator by lazy { TtlParametersValidator() }

    override val inAppConfigTtlValidator: InAppConfigTtlValidator by lazy { InAppConfigTtlValidator() }

    override val slidingExpirationParametersValidator: TimeSpanPositiveValidator by lazy { TimeSpanPositiveValidator() }
    override val mobileConfigSettingsManager: MobileConfigSettingsManagerImpl by lazy {
        MobileConfigSettingsManagerImpl(appContext, sessionStorageManager, timeProvider)
    }
    override val inAppMapper: InAppMapper by lazy { InAppMapper() }

    override val mindboxNotificationManager: MindboxNotificationManager by lazy {
        MindboxNotificationManagerImpl(
            context = appContext,
            requestPermissionManager = requestPermissionManager
        )
    }

    override val requestPermissionManager: RequestPermissionManager
        get() = RequestPermissionManagerImpl()
    override val frequencyDataFiller: FrequencyDataFiller
        get() = FrequencyDataFiller()
    override val frequencyValidator: FrequencyValidator
        get() = FrequencyValidator()
    override val migrationManager: MigrationManager by lazy {
        MigrationManager(appContext)
    }
    override val timeProvider: SystemTimeProvider by lazy {
        SystemTimeProvider()
    }

    override val gson: Gson by lazy {
        GsonBuilder()
            .registerTypeAdapterFactory(
                RuntimeTypeAdapterFactory
                    .of(
                        FrequencyDto::class.java,
                        Constants.TYPE_JSON_NAME,
                        true
                    ).registerSubtype(
                        FrequencyDto.FrequencyOnceDto::class.java,
                        FrequencyDto.FrequencyOnceDto.FREQUENCY_ONCE_JSON_NAME
                    ).registerSubtype(
                        FrequencyDto.FrequencyPeriodicDto::class.java,
                        FrequencyDto.FrequencyPeriodicDto.FREQUENCY_PERIODIC_JSON_NAME
                    )
            ).registerTypeAdapterFactory(
                RuntimeTypeAdapterFactory
                    .of(
                        PayloadBlankDto::class.java,
                        Constants.TYPE_JSON_NAME,
                        true
                    ).registerSubtype(
                        PayloadBlankDto.ModalWindowBlankDto::class.java,
                        PayloadDto.ModalWindowDto.MODAL_JSON_NAME
                    ).registerSubtype(
                        PayloadBlankDto.SnackBarBlankDto::class.java,
                        PayloadDto.SnackbarDto.SNACKBAR_JSON_NAME
                    )
            ).registerTypeAdapterFactory(
                RuntimeTypeAdapterFactory
                    .of(
                        ElementDto::class.java,
                        Constants.TYPE_JSON_NAME, true
                    ).registerSubtype(
                        ElementDto.CloseButtonElementDto::class.java,
                        ElementDto.CloseButtonElementDto.CLOSE_BUTTON_ELEMENT_JSON_NAME
                    )
            ).registerTypeAdapterFactory(
                RuntimeTypeAdapterFactory
                    .of(
                        BackgroundDto.LayerDto.ImageLayerDto.SourceDto::class.java,
                        Constants.TYPE_JSON_NAME,
                        true
                    ).registerSubtype(
                        BackgroundDto.LayerDto.ImageLayerDto.SourceDto.UrlSourceDto::class.java,
                        BackgroundDto.LayerDto.ImageLayerDto.SourceDto.UrlSourceDto.URL_SOURCE_JSON_NAME
                    )
            ).registerTypeAdapterFactory(
                RuntimeTypeAdapterFactory
                    .of(
                        BackgroundDto.LayerDto.ImageLayerDto.ActionDto::class.java,
                        Constants.TYPE_JSON_NAME,
                        true
                    ).registerSubtype(
                        RedirectUrlActionDto::class.java,
                        RedirectUrlActionDto.REDIRECT_URL_ACTION_TYPE_JSON_NAME
                    ).registerSubtype(
                        PushPermissionActionDto::class.java,
                        PushPermissionActionDto.PUSH_PERMISSION_TYPE_JSON_NAME
                    )
            ).registerTypeAdapterFactory(
                RuntimeTypeAdapterFactory
                    .of(
                        BackgroundDto.LayerDto::class.java,
                        Constants.TYPE_JSON_NAME,
                        true
                    ).registerSubtype(
                        BackgroundDto.LayerDto.ImageLayerDto::class.java,
                        BackgroundDto.LayerDto.ImageLayerDto.IMAGE_TYPE_JSON_NAME
                    )
            ).registerTypeAdapterFactory(
                RuntimeTypeAdapterFactory
                    .of(
                        PayloadDto::class.java,
                        Constants.TYPE_JSON_NAME, true
                    ).registerSubtype(
                        PayloadDto.ModalWindowDto::class.java,
                        PayloadDto.ModalWindowDto.MODAL_JSON_NAME
                    ).registerSubtype(
                        PayloadDto.SnackbarDto::class.java,
                        PayloadDto.SnackbarDto.SNACKBAR_JSON_NAME
                    )
            ).registerTypeAdapterFactory(
                RuntimeTypeAdapterFactory
                    .of(
                        TreeTargetingDto::class.java,
                        Constants.TYPE_JSON_NAME,
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
                    ).registerSubtype(
                        TreeTargetingDto.VisitNodeDto::class.java,
                        TreeTargetingDto.VisitNodeDto.VISIT_JSON_NAME
                    ).registerSubtype(
                        TreeTargetingDto.PushPermissionDto::class.java,
                        TreeTargetingDto.PushPermissionDto.PUSH_PERMISSION_JSON_NAME
                    )
            ).create()
    }
}
