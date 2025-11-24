package cloud.mindbox.mobile_sdk.di.modules

import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.presentation.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal fun PresentationModule(
    domainModule: DomainModule,
    monitoringModule: MonitoringModule,
    apiModule: ApiModule,
    dataModule: DataModule,
    appContextModule: AppContextModule
): PresentationModule = object : PresentationModule,
    ApiModule by apiModule,
    DataModule by dataModule,
    DomainModule by domainModule,
    MonitoringModule by monitoringModule,
    AppContextModule by appContextModule {

    override val inAppMessageViewDisplayer by lazy {
        InAppMessageViewDisplayerImpl(inAppImageSizeStorage)
    }

    override val inAppMessageManager by lazy {
        InAppMessageManagerImpl(
            inAppMessageViewDisplayer = inAppMessageViewDisplayer,
            inAppInteractor = inAppInteractor,
            inAppScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + Mindbox.coroutineExceptionHandler),
            monitoringInteractor = monitoringInteractor,
            sessionStorageManager = sessionStorageManager,
            userVisitManager = userVisitManager,
            inAppMessageDelayedManager = inAppMessageDelayedManager
        )
    }
    override val clipboardManager: ClipboardManager by lazy {
        ClipboardManagerImpl(context = appContext)
    }
    override val activityManager: ActivityManager by lazy {
        ActivityManagerImpl(callbackInteractor = callbackInteractor, context = appContext)
    }
}
