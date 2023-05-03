package cloud.mindbox.mobile_sdk.di.modules

import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManagerImpl
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageViewDisplayerImpl
import kotlinx.coroutines.Dispatchers


internal fun PresentationModule(
    domainModule: DomainModule,
    monitoringModule: MonitoringModule,
    apiModule: ApiModule,
): PresentationModule = object : PresentationModule,
    ApiModule by apiModule,
    DomainModule by domainModule,
    MonitoringModule by monitoringModule {

    override val inAppMessageViewDisplayer by lazy {
        InAppMessageViewDisplayerImpl()
    }

    override val inAppMessageManager by lazy {
        InAppMessageManagerImpl(
            inAppMessageViewDisplayer = inAppMessageViewDisplayer,
            inAppInteractor = inAppInteractor,
            defaultDispatcher = Dispatchers.IO,
            monitoringInteractor = monitoringInteractor
        )
    }
}