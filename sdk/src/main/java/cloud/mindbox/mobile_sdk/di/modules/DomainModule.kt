package cloud.mindbox.mobile_sdk.di.modules


import cloud.mindbox.mobile_sdk.abtests.CustomerAbMixer
import cloud.mindbox.mobile_sdk.abtests.CustomerAbMixerImpl
import cloud.mindbox.mobile_sdk.abtests.InAppABTestLogic
import cloud.mindbox.mobile_sdk.inapp.domain.CallbackInteractorImpl
import cloud.mindbox.mobile_sdk.inapp.domain.InAppProcessingManagerImpl
import cloud.mindbox.mobile_sdk.inapp.domain.InAppEventManagerImpl
import cloud.mindbox.mobile_sdk.inapp.domain.InAppFilteringManagerImpl
import cloud.mindbox.mobile_sdk.inapp.domain.InAppInteractorImpl
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.CallbackInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppProcessingManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppEventManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFilteringManager
import cloud.mindbox.mobile_sdk.managers.UserVisitManager
import cloud.mindbox.mobile_sdk.managers.UserVisitManagerImpl


internal fun DomainModule(
    dataModule: DataModule,
    apiModule: ApiModule
): DomainModule = object : DomainModule,
    DataModule by dataModule,
    ApiModule by apiModule {

    override val inAppInteractor: InAppInteractor by lazy {
        InAppInteractorImpl(
            mobileConfigRepository = mobileConfigRepository,
            inAppRepository = inAppRepository,
            inAppSegmentationRepository = inAppSegmentationRepository,
            inAppFilteringManager = inAppFilteringManager,
            inAppEventManager = inAppEventManager,
            inAppProcessingManager = inAppProcessingManager,
            inAppABTestLogic = inAppABTestLogic
        )
    }
    override val callbackInteractor: CallbackInteractor by lazy {
        CallbackInteractorImpl(callbackRepository)
    }

    override val inAppProcessingManager: InAppProcessingManager by lazy {
        InAppProcessingManagerImpl(
            inAppGeoRepository = inAppGeoRepository,
            inAppSegmentationRepository = inAppSegmentationRepository,
            inAppContentFetcher = inAppContentFetcher,
            inAppRepository = inAppRepository
        )
    }

    override val inAppEventManager: InAppEventManager
        get() = InAppEventManagerImpl()

    override val inAppFilteringManager: InAppFilteringManager
        get() = InAppFilteringManagerImpl(inAppRepository = inAppRepository)

    override val inAppABTestLogic: InAppABTestLogic
        get() = InAppABTestLogic(
            mixer = customerAbMixer,
            repository = mobileConfigRepository
        )

    override val userVisitManager: UserVisitManager
        get() = UserVisitManagerImpl()

    override val customerAbMixer: CustomerAbMixer
         get() = CustomerAbMixerImpl()
}