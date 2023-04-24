package cloud.mindbox.mobile_sdk.di

import android.app.Application
import android.content.Context
import cloud.mindbox.mobile_sdk.di.modules.ApiModule
import cloud.mindbox.mobile_sdk.di.modules.AppContextModule
import cloud.mindbox.mobile_sdk.di.modules.AppModule
import cloud.mindbox.mobile_sdk.di.modules.DomainModule
import cloud.mindbox.mobile_sdk.logger.mindboxLogD

internal object MindboxDI {

    internal lateinit var appModule: AppModule

    fun isInitialized() = MindboxDI::appModule.isInitialized

    fun init(appContext: Context) {
        if (isInitialized()) return

        mindboxLogD("MindboxDI init in ${Thread.currentThread().name}")

        val applicationContextModule = AppContextModule(appContext.applicationContext as Application)
        val apiModule = ApiModule(applicationContextModule)
        val dataModule = DataModule(applicationContextModule, apiModule)
        val domainModule = DomainModule(dataModule)
        val monitoringModule = MonitoringModule(applicationContextModule, dataModule, apiModule)
        val presentationModule = PresentationModule(domainModule, monitoringModule)

        appModule = AppModule(
            applicationContextModule = applicationContextModule,
            apiModule = apiModule,
            dataModule = dataModule,
            domainModule = domainModule,
            monitoringModule = monitoringModule,
            presentationModule = presentationModule,
        )
    }
}
