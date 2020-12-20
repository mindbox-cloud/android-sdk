package com.mindbox.androidsdk.screens

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import cloud.mindbox.mobile_sdk.Configuration
import cloud.mindbox.mobile_sdk.Mindbox
import com.mindbox.androidsdk.Prefs
import com.mindbox.androidsdk.R
import kotlinx.android.synthetic.main.fragment_entering_data.*

class EnteringDataFragment(callback: () -> Unit) :
    Fragment(R.layout.fragment_entering_data) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupFields()

        nextButton.setOnClickListener {
            initSdk()
        }
    }

    private fun setupFields() {
        loadProgress.visibility = View.VISIBLE
        Mindbox.init(this.requireContext(), Configuration()) { deviceId, installId ->
            deviceUuidData.setText(deviceId ?: "")
            installationIdData.setText(installId ?: "")
        }

        endpointData.setText(Prefs.enteredEndpoint)
        endpointData.setSelection(endpointData.text.toString().length)

        loadProgress.visibility = View.GONE
    }

    private fun initSdk() {
        val endpoint = endpointData.text.toString()
        val fbToken = deviceUuidData.text.toString()
        val installId = installationIdData.text.toString()

        if (endpoint.isNotEmpty() && endpoint != Prefs.enteredEndpoint) {
            Prefs.enteredEndpoint = endpoint
        }

        Mindbox.setInstallationId(installationIdData.text.toString())
    }
}