package com.mindbox.example

import android.app.Application
import cloud.mindbox.mindbox_firebase.MindboxFirebase
import cloud.mindbox.mindbox_huawei.MindboxHuawei
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

        //https://developers.mindbox.ru/docs/android-sdk-initialization
        val configuration = MindboxConfiguration.Builder(
            context = applicationContext,
            domain = "api.mindbox.ru",//paste your domain address
            endpointId = "Mpush-test.AndroidAppExample"//paste your domain address
        )
            .shouldCreateCustomer(true)
            .subscribeCustomerIfCreated(true)
            .build()

        //https://developers.mindbox.ru/docs/android-sdk-methods#initpushservices
        Mindbox.initPushServices(
            context = applicationContext,
            pushServices = listOf(MindboxFirebase, MindboxHuawei)
        )
        //https://developers.mindbox.ru/docs/android-sdk-methods#init
        Mindbox.init(
            application = this,
            configuration = configuration,
            pushServices = listOf(MindboxFirebase, MindboxHuawei)
        )

        //https://developers.mindbox.ru/docs/android-sdk-methods#setloglevel
        if (BuildConfig.DEBUG) {
            Mindbox.setLogLevel(level = Level.DEBUG)
        }

        //https://developers.mindbox.ru/docs/in-app#inappcallback
        chooseInappCallback(selectedInappCallback = RegisterInappCallback.DEFAULT)
        //https://developers.mindbox.ru/docs/android-sdk-methods#setmessagehandling-since-261
        chooseNotificationImageHandler(selectedImageHandler = NotificationImageHandler.DEFAULT)
    }
}