package com.mindbox.example

import android.app.Application
import cloud.mindbox.mindbox_firebase.MindboxFirebase
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.logger.Level

class ExampleApp : Application() {
    companion object {
        private var privateApplication: Application? = null
        val application: Application
            get() = privateApplication!!
    }

    override fun onCreate() {
        super.onCreate()
        privateApplication = this
        val configuration = MindboxConfiguration.Builder(
            context = applicationContext,
            domain = "",//paste your domain address
            endpointId = ""//paste your domain address
        )
            .shouldCreateCustomer(true)
            .subscribeCustomerIfCreated(true)
            .build()

        //https://developers.mindbox.ru/docs/android-sdk-methods#init
        Mindbox.init(
            application = this,
            configuration = configuration,
            pushServices = listOf(MindboxFirebase)
        )

        //https://developers.mindbox.ru/docs/android-sdk-methods#setloglevel
        if (BuildConfig.DEBUG) {
            Mindbox.setLogLevel(level = Level.DEBUG)
        }
    }
}