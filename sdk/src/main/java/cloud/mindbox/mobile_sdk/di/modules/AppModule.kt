package cloud.mindbox.mobile_sdk.di.modules

import android.content.pm.ApplicationInfo
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler


internal fun AppModule(
    applicationContextModule: AppContextModule,
    domainModule: DomainModule,
    monitoringModule: MonitoringModule,
    presentationModule: PresentationModule,
    dataModule: DataModule,
    apiModule: ApiModule,
): AppModule = object : AppModule,
    AppContextModule by applicationContextModule,
    DomainModule by domainModule,
    MonitoringModule by monitoringModule,
    PresentationModule by presentationModule,
    DataModule by dataModule,
    ApiModule by apiModule {

    override fun isDebug(): Boolean = LoggingExceptionHandler.runCatching(defaultValue = false) {
        (applicationContextModule.appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

}