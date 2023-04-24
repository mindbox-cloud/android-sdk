package cloud.mindbox.mobile_sdk.di.modules

import cloud.mindbox.mobile_sdk.inapp.domain.InAppChoosingManagerImpl
import cloud.mindbox.mobile_sdk.inapp.domain.InAppEventManagerImpl
import cloud.mindbox.mobile_sdk.inapp.domain.InAppFilteringManagerImpl
import cloud.mindbox.mobile_sdk.inapp.domain.InAppInteractorImpl
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.InAppInteractor
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppChoosingManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppEventManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFilteringManager


internal fun DomainModule(dataModule: DataModule) =
    object : DomainModule {

        override val inAppInteractor: InAppInteractor by lazy {
            InAppInteractorImpl(
                mobileConfigRepository = dataModule.mobileConfigRepository,
                inAppRepository = dataModule.inAppRepository,
                inAppSegmentationRepository = dataModule.inAppSegmentationRepository,
                inAppFilteringManager = inAppFilteringManager,
                inAppEventManager = inAppEventManager,
                inAppChoosingManager = inAppChoosingManager
            )
        }

        override val inAppChoosingManager: InAppChoosingManager by lazy {
            InAppChoosingManagerImpl(
                inAppGeoRepository = dataModule.inAppGeoRepository,
                inAppSegmentationRepository = dataModule.inAppSegmentationRepository
            )
        }

        override val inAppEventManager: InAppEventManager
            get() = InAppEventManagerImpl()

        override val inAppFilteringManager: InAppFilteringManager
            get() = InAppFilteringManagerImpl(
                inAppRepository = dataModule.inAppRepository
            )
    }