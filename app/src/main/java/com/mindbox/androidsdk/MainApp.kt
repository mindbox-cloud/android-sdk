package com.mindbox.androidsdk

import android.app.Application
import cloud.mindbox.mobile_sdk.Configuration
import cloud.mindbox.mobile_sdk.Mindbox

class MainApp: Application() {

    override fun onCreate() {
        super.onCreate()

        Mindbox.init(this, Configuration())
    }
}