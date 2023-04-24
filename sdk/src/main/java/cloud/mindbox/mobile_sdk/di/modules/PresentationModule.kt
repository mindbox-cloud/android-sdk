package cloud.mindbox.mobile_sdk.di

import cloud.mindbox.mobile_sdk.di.modules.DomainModule
import cloud.mindbox.mobile_sdk.di.modules.MonitoringModule
import cloud.mindbox.mobile_sdk.di.modules.PresentationModule
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManagerImpl
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageViewDisplayerImpl
import kotlinx.coroutines.Dispatchers


internal fun PresentationModule(
    domainModule: DomainModule,
    monitoringModule: MonitoringModule
) = object: PresentationModule {

        override val inAppMessageViewDisplayer by lazy {
            InAppMessageViewDisplayerImpl()
        }

        override val inAppMessageManager by lazy {
            InAppMessageManagerImpl(
                inAppMessageViewDisplayer = inAppMessageViewDisplayer,
                inAppInteractor = domainModule.inAppInteractor,
                defaultDispatcher = Dispatchers.IO,
                monitoringInteractor = monitoringModule.monitoringInteractor
            )
        }
    }