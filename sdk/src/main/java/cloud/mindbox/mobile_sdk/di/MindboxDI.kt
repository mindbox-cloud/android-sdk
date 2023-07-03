package cloud.mindbox.mobile_sdk.di

import android.app.Application
import android.content.Context
import cloud.mindbox.mobile_sdk.di.modules.*
import cloud.mindbox.mobile_sdk.logger.mindboxLogD

internal object MindboxDI {

    internal lateinit var appModule: AppModule

    fun isInitialized() = MindboxDI::appModule.isInitialized

    fun init(appContext: Context) {
        if (isInitialized()) return

        mindboxLogD("MindboxDI init in ${Thread.currentThread().name}")

        val appContextModule = AppContextModule(
            application = appContext.applicationContext as Application
        )
        val apiModule = ApiModule(
            appContextModule = appContextModule
        )
        val dataModule = DataModule(
            appContextModule = appContextModule,
            apiModule = apiModule
        )
        val domainModule = DomainModule(
            dataModule = dataModule,
            apiModule = apiModule
        )
        val monitoringModule = MonitoringModule(
            appContextModule = appContextModule,
            dataModule = dataModule,
            apiModule = apiModule,
        )
        val presentationModule = PresentationModule(
            domainModule = domainModule,
            monitoringModule = monitoringModule,
            apiModule = apiModule,
            appContextModule = appContextModule
        )

        appModule = AppModule(
            applicationContextModule = appContextModule,
            apiModule = apiModule,
            dataModule = dataModule,
            domainModule = domainModule,
            monitoringModule = monitoringModule,
            presentationModule = presentationModule,
        )
    }
}
