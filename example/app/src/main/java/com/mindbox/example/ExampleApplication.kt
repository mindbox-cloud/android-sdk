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
        const val RU_STORE_PROJECT_ID = "" //paste your RuStore project id
        private var privateApplication: Application? = null
        val application: Application
            get() = privateApplication!!
    }

    override fun onCreate() {
        super.onCreate()
        privateApplication = this

        //https://developers.mindbox.ru/docs/android-sdk-initialization
        val configuration = MindboxConfiguration.Builder(
            context = applicationContext,
            domain = "",//paste your domain address
            endpointId = ""//paste your domain address
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
