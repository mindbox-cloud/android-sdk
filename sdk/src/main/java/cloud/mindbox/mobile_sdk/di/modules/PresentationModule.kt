package cloud.mindbox.mobile_sdk.di.modules

import cloud.mindbox.mobile_sdk.embedded.EmbeddedBlocksRegistry
import cloud.mindbox.mobile_sdk.embedded.EmbeddedBlocksRegistryImpl
import cloud.mindbox.mobile_sdk.inapp.presentation.*
import cloud.mindbox.mobile_sdk.inapp.presentation.view.MindboxWebPageRegistry
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
            monitoringInteractor = monitoringInteractor,
            sessionStorageManager = sessionStorageManager,
            userVisitManager = userVisitManager,
            inAppMessageDelayedManager = inAppMessageDelayedManager,
            timeProvider = timeProvider,
            featureToggleManager = featureToggleManager
        )
    }
    override val clipboardManager: ClipboardManager by lazy {
        ClipboardManagerImpl(context = appContext)
    }

    override val webPageRegistry: MindboxWebPageRegistry by lazy {
        MindboxWebPageRegistry()
    }

    private val embeddedBlocksRegistryLazy: Lazy<EmbeddedBlocksRegistry> = lazy {
        EmbeddedBlocksRegistryImpl(inAppInteractor = inAppInteractor)
    }

    override val embeddedBlocksRegistry: EmbeddedBlocksRegistry
        get() = embeddedBlocksRegistryLazy.value

    override val embeddedBlocksRegistryIfCreated: EmbeddedBlocksRegistry?
        get() = embeddedBlocksRegistryLazy.takeIf { registry -> registry.isInitialized() }?.value

    override val activityManager: ActivityManager by lazy {
        ActivityManagerImpl(callbackInteractor = callbackInteractor, context = appContext)
    }
}
