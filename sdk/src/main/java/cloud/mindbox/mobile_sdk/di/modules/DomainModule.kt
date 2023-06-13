package cloud.mindbox.mobile_sdk.di.modules

import cloud.mindbox.mobile_sdk.abtests.CustomerAbMixer
import cloud.mindbox.mobile_sdk.abtests.CustomerAbMixerImpl
import cloud.mindbox.mobile_sdk.inapp.domain.InAppChoosingManagerImpl
import cloud.mindbox.mobile_sdk.inapp.domain.InAppEventManagerImpl
import cloud.mindbox.mobile_sdk.inapp.domain.InAppFilteringManagerImpl
import cloud.mindbox.mobile_sdk.inapp.domain.InAppInteractorImpl
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppChoosingManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppEventManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFilteringManager


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
            inAppChoosingManager = inAppChoosingManager
        )
    }

    override val inAppChoosingManager: InAppChoosingManager by lazy {
        InAppChoosingManagerImpl(
            inAppGeoRepository = inAppGeoRepository,
            inAppSegmentationRepository = inAppSegmentationRepository,
            inAppContentFetcher = inAppContentFetcher,
        )
    }

    override val inAppEventManager: InAppEventManager
        get() = InAppEventManagerImpl()

    override val inAppFilteringManager: InAppFilteringManager
        get() = InAppFilteringManagerImpl(inAppRepository = inAppRepository)

    override val customerAbMixer: CustomerAbMixer
         get() = CustomerAbMixerImpl()
}