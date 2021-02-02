package com.mindbox.androidsdk.screens

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import cloud.mindbox.mobile_sdk.Mindbox
import com.mindbox.androidsdk.R
import kotlinx.android.synthetic.main.fragment_complete_data.*

class CompleteDataFragment(
    private val domain: String,
    private val endpoint: String,
    private val deviceId: String,
    private val installId: String
) :
    Fragment(R.layout.fragment_complete_data) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fillData()
        updateButton.setOnClickListener { fillData() }
    }

    private fun fillData() {
        initParams.text = """
            domain: $domain
            
            endpoint: $endpoint
            
            deviceUUID: $deviceId
            
            installId: $installId
        """.trimIndent()

        sdkData.text = """
                deviceUUID: ${Mindbox.getDeviceUuid()}
                
                save token: ${Mindbox.getFmsToken()}
                
                save token date: ${Mindbox.getFmsTokenSaveDate()}
                
                SDK version: ${Mindbox.getSdkVersion()}
            """.trimIndent()
    }
}