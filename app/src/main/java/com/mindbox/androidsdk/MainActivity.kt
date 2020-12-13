package com.mindbox.androidsdk

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cloud.mindbox.mobile_sdk.Configuration

class MainActivity : AppCompatActivity(R.layout.activity_main) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration().getDeviceUuid(this)
    }

}