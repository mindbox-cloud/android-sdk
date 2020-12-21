package com.mindbox.androidsdk.screens

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import cloud.mindbox.mobile_sdk.BuildConfig
import cloud.mindbox.mobile_sdk.Mindbox
import com.mindbox.androidsdk.R
import kotlinx.android.synthetic.main.fragment_complete_data.*

class CompleteDataFragment(private val endpoint: String, private val deviceId: String, private val installId: String) :
    Fragment(R.layout.fragment_complete_data) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initParams.text = """
            endpoint: $endpoint
            
            deviceUUID: $deviceId
            
            installId: $installId
        """.trimIndent()

        Mindbox.getSdkData { deviceUUID, token, sdkVersion ->
            sdkData.text = """
                deviceUUID: $deviceUUID
                
                save token date: $token
                
                SDK version: $sdkVersion
            """.trimIndent()
        }
    }
}