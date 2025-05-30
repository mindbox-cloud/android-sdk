package cloud.mindbox.mobile_sdk.di.modules

import cloud.mindbox.mobile_sdk.abtests.CustomerAbMixer
import cloud.mindbox.mobile_sdk.abtests.CustomerAbMixerImpl
import cloud.mindbox.mobile_sdk.abtests.InAppABTestLogic
import cloud.mindbox.mobile_sdk.inapp.domain.*
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.CallbackInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppEventManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFilteringManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFrequencyManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppProcessingManager
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
            inAppFilteringManager = inAppFilteringManager,
            inAppEventManager = inAppEventManager,
            inAppProcessingManager = inAppProcessingManager,
            inAppABTestLogic = inAppABTestLogic,
            inAppFrequencyManager = inAppFrequencyManager,
            allAllowInAppShowLimitChecker = allAllowInAppShowLimitChecker,
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
        get() = InAppFilteringManagerImpl(
            inAppRepository = inAppRepository
        )

    override val inAppABTestLogic: InAppABTestLogic
        get() = InAppABTestLogic(
            mixer = customerAbMixer,
            repository = mobileConfigRepository
        )

    override val userVisitManager: UserVisitManager
        get() = UserVisitManagerImpl()

    override val inAppFrequencyManager: InAppFrequencyManager
        get() = InAppFrequencyManagerImpl(inAppRepository)

    override val customerAbMixer: CustomerAbMixer
        get() = CustomerAbMixerImpl()
}
