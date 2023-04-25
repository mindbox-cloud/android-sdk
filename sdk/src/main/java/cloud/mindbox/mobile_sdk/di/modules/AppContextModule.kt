package cloud.mindbox.mobile_sdk.di.modules

import android.app.Application


internal fun AppContextModule(
    application: Application
): AppContextModule = object : AppContextModule {

    override val appContext: Application
        get() = application

}