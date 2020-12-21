package com.mindbox.androidsdk.screens

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import cloud.mindbox.mobile_sdk.Configuration
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.models.MindboxResponse
import com.mindbox.androidsdk.Prefs
import com.mindbox.androidsdk.R
import kotlinx.android.synthetic.main.fragment_entering_data.*
import org.json.JSONObject

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
        loadProgress.visibility = View.VISIBLE
        val endpoint = endpointData.text.toString()
        val deviceId = deviceUuidData.text.toString()
        val installId = installationIdData.text.toString()

        if (endpoint.isNotEmpty() && endpoint != Prefs.enteredEndpoint) {
            Prefs.enteredEndpoint = endpoint
        }

        Mindbox.registerSdk(this.requireContext(), endpoint, deviceId, installId) { response ->
            activity?.runOnUiThread {
                loadProgress.visibility = View.GONE
                if (response is MindboxResponse.Error) {
                    errorContainer.text =
                        """code: ${response.status}
                            |
                            |message: ${response.message}
                            |
                            |error body: ${JSONObject(response.errorBody?.string())}
                        """.trimMargin()
                }
            }
        }
    }
}