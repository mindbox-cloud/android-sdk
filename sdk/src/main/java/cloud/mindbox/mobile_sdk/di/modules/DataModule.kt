package cloud.mindbox.mobile_sdk.di.modules

import cloud.mindbox.mobile_sdk.inapp.data.managers.GeoSerializationManagerImpl
import cloud.mindbox.mobile_sdk.inapp.data.managers.InAppSerializationManagerImpl
import cloud.mindbox.mobile_sdk.inapp.data.managers.MobileConfigSerializationManagerImpl
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.inapp.data.repositories.*
import cloud.mindbox.mobile_sdk.inapp.data.validators.*
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.GeoSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.MobileConfigSerializationManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.*
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.validators.InAppValidator
import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.ElementDto
import cloud.mindbox.mobile_sdk.inapp.domain.models.PayloadDto
import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import cloud.mindbox.mobile_sdk.monitoring.data.validators.MonitoringValidator
import cloud.mindbox.mobile_sdk.utils.Constants
import cloud.mindbox.mobile_sdk.utils.RuntimeTypeAdapterFactory
import com.google.gson.Gson
import com.google.gson.GsonBuilder


internal fun DataModule(
    appContextModule: AppContextModule,
    apiModule: ApiModule
): DataModule = object : DataModule,
    AppContextModule by appContextModule,
    ApiModule by apiModule {

    override val sessionStorageManager: SessionStorageManager by lazy { SessionStorageManager() }

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
        )
    }

    override val mobileConfigSerializationManager: MobileConfigSerializationManager
        get() = MobileConfigSerializationManagerImpl(gson = gson)

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
            sdkVersionValidator,
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

    override val inAppMapper: InAppMapper by lazy { InAppMapper() }

    override val gson: Gson by lazy {
        GsonBuilder().registerTypeAdapterFactory(
            RuntimeTypeAdapterFactory.of(
                ElementDto::class.java,
                Constants.TYPE_JSON_NAME, true
            ).registerSubtype(
                ElementDto.CloseButtonElementDto::class.java,
               ElementDto.CloseButtonElementDto.CLOSE_BUTTON_ELEMENT_JSON_NAME
            )
        ).registerTypeAdapterFactory(
            RuntimeTypeAdapterFactory.of(
                BackgroundDto.LayerDto.ImageLayerDto.SourceDto::class.java,
                Constants.TYPE_JSON_NAME,
                true
            ).registerSubtype(
                    BackgroundDto.LayerDto.ImageLayerDto.SourceDto.UrlSourceDto::class.java,
                    BackgroundDto.LayerDto.ImageLayerDto.SourceDto.UrlSourceDto.URL_SOURCE_JSON_NAME
                )
        )
            .registerTypeAdapterFactory(
                RuntimeTypeAdapterFactory.of(
                    BackgroundDto.LayerDto.ImageLayerDto.ActionDto::class.java,
                    Constants.TYPE_JSON_NAME,
                    true
                ).registerSubtype(
                    BackgroundDto.LayerDto.ImageLayerDto.ActionDto.RedirectUrlActionDto::class.java,
                    BackgroundDto.LayerDto.ImageLayerDto.ActionDto.RedirectUrlActionDto.REDIRECT_URL_ACTION_TYPE_JSON_NAME
                )
            ).registerTypeAdapterFactory(
                RuntimeTypeAdapterFactory.of(
                    BackgroundDto.LayerDto::class.java,
                    Constants.TYPE_JSON_NAME,
                    true
                ).registerSubtype(
                    BackgroundDto.LayerDto.ImageLayerDto::class.java,
                    BackgroundDto.LayerDto.ImageLayerDto.IMAGE_TYPE_JSON_NAME
                )
            ).registerTypeAdapterFactory(
                RuntimeTypeAdapterFactory.of(
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
                RuntimeTypeAdapterFactory.of(
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
                )
            ).create()
    }

}