package com.mindbox.androidsdk.screens

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
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
    }
}