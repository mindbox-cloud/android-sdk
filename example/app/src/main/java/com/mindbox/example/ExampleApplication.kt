package com.mindbox.example

import android.app.Application
import cloud.mindbox.mindbox_firebase.MindboxFirebase
import cloud.mindbox.mindbox_huawei.MindboxHuawei
import cloud.mindbox.mindbox_rustore.MindboxRuStore
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.logger.Level


class ExampleApp : Application() {

    companion object {
        // Value is injected from example.properties via BuildConfig.
        // To set it manually, replace BuildConfig.MINDBOX_RUSTORE_PROJECT_ID with a string literal.
        val RU_STORE_PROJECT_ID: String = BuildConfig.MINDBOX_RUSTORE_PROJECT_ID //paste your RuStore project id
        private var privateApplication: Application? = null
        val application: Application
            get() = privateApplication!!
    }

    override fun onCreate() {
        super.onCreate()
        privateApplication = this

        //https://developers.mindbox.ru/docs/android-sdk-initialization
        // Values are injected from example.properties via BuildConfig.
        // To set them manually, replace BuildConfig.MINDBOX_DOMAIN / MINDBOX_ENDPOINT_ID with string literals.
        val domain = BuildConfig.MINDBOX_DOMAIN.ifEmpty { "" } //paste your domain address
        val endpointId = BuildConfig.MINDBOX_ENDPOINT_ID.ifEmpty { "" } //paste your endpointId

        val configuration = MindboxConfiguration.Builder(
            context = applicationContext,
            domain = domain,
            endpointId = endpointId
        )
            .shouldCreateCustomer(true)
            .subscribeCustomerIfCreated(true)
            .build()

        //https://developers.mindbox.ru/docs/android-sdk-methods#setloglevel
        if (BuildConfig.DEBUG) {
            Mindbox.setLogLevel(level = Level.DEBUG)
        }

        //https://developers.mindbox.ru/docs/android-sdk-methods#initpushservices
        Mindbox.initPushServices(
            context = applicationContext,
            pushServices = listOf(MindboxFirebase, MindboxHuawei, MindboxRuStore)
        )
        //https://developers.mindbox.ru/docs/android-sdk-methods#init
        Mindbox.init(
            application = this,
            configuration = configuration,
            pushServices = listOf(MindboxFirebase, MindboxHuawei, MindboxRuStore)
        )

        //https://developers.mindbox.ru/docs/in-app#inappcallback
        chooseInappCallback(selectedInappCallback = RegisterInappCallback.DEFAULT)
        //https://developers.mindbox.ru/docs/android-sdk-methods#setmessagehandling-since-261
        chooseNotificationImageHandler(selectedImageHandler = NotificationImageHandler.DEFAULT)
    }
}
