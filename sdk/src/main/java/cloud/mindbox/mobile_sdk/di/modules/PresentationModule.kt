package cloud.mindbox.mobile_sdk.di.modules

import cloud.mindbox.mobile_sdk.inapp.presentation.ActivityManager
import cloud.mindbox.mobile_sdk.inapp.presentation.ActivityManagerImpl
import cloud.mindbox.mobile_sdk.inapp.presentation.ClipboardManager
import cloud.mindbox.mobile_sdk.inapp.presentation.ClipboardManagerImpl
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManagerImpl
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageViewDisplayerImpl
import kotlinx.coroutines.Dispatchers


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
            defaultDispatcher = Dispatchers.IO,
            monitoringInteractor = monitoringInteractor
        )
    }
    override val clipboardManager: ClipboardManager by lazy {
        ClipboardManagerImpl(context = appContext)
    }
    override val activityManager: ActivityManager by lazy {
        ActivityManagerImpl(callbackInteractor = callbackInteractor, context = appContext)
    }
}