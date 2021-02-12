package com.mindbox.androidsdk.screens

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import cloud.mindbox.mobile_sdk.InitializeMindboxException
import cloud.mindbox.mobile_sdk.Mindbox
import com.mindbox.androidsdk.R
import kotlinx.android.synthetic.main.fragment_complete_data.*
import java.util.*

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
        sendPushEvent.setOnClickListener { sendPushDeliveryEvents() }
    }

    private fun fillData() {
        initParams.text = """
            domain: $domain
            
            endpoint: $endpoint
            
            deviceUUID: $deviceId
            
            installId: $installId
        """.trimIndent()

        sdkData.text = """
                deviceUUID: ${
                    try {
                        Mindbox.getDeviceUuid()
                    } catch (e: InitializeMindboxException) {
                        "null"
                    }
                }
                
                save token: ${Mindbox.getFmsToken()}
                
                save token date: ${Mindbox.getFmsTokenSaveDate()}
                
                SDK version: ${Mindbox.getSdkVersion()}
            """.trimIndent()
    }

    private fun sendPushDeliveryEvents() {
        try {
            val count: Int = countPushEvents.text.toString().toInt()

            for (i in 1..count) {
                Mindbox.onPushReceived(
                    applicationContext = requireContext(),
                    uniqKey = UUID.randomUUID().toString())
            }

        } catch (e: NumberFormatException) {
            Toast.makeText(requireContext(), "It's not a number", Toast.LENGTH_SHORT).show()
        }
    }
}