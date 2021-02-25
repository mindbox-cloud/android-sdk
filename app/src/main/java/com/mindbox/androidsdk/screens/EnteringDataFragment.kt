package com.mindbox.androidsdk.screens

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import cloud.mindbox.mobile_sdk.Configuration
import cloud.mindbox.mobile_sdk.InitializeMindboxException
import cloud.mindbox.mobile_sdk.Mindbox
import com.mindbox.androidsdk.InitializeData
import com.mindbox.androidsdk.Prefs
import com.mindbox.androidsdk.R
import kotlinx.android.synthetic.main.fragment_entering_data.*

class EnteringDataFragment(private val callback: (InitializeData) -> Unit) :
    Fragment(R.layout.fragment_entering_data) {

    companion object {
        private const val DEFAULT_DOMAIN = "api.mindbox.ru"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupFields()

        nextButton.setOnClickListener {
            initSdk()
        }
    }

    private fun setupFields() {
        loadProgress.visibility = View.VISIBLE

        domainData.setText(Prefs.enteredDomain)
        domainData.setSelection(domainData.text.toString().length)

        endpointData.setText(Prefs.enteredEndpoint)
        endpointData.setSelection(endpointData.text.toString().length)

        loadProgress.visibility = View.GONE
    }

    private fun initSdk() {
        loadProgress.visibility = View.VISIBLE
        val domain = domainData.text.toString()
        val endpoint = endpointData.text.toString()
        val deviceId = deviceUuidData.text.toString()
        val installId = installationIdData.text.toString()
        val subscribeValue = subscribeView.isChecked

        if (domain.isNotEmpty() && domain != Prefs.enteredDomain) {
            Prefs.enteredDomain = domain
        }

        if (endpoint.isNotEmpty() && endpoint != Prefs.enteredEndpoint) {
            Prefs.enteredEndpoint = endpoint
        }

        val notEmptyDomain = when {
            domain.isNotEmpty() -> {
                domain
            }
            Prefs.enteredDomain.isNotEmpty() -> {
                Prefs.enteredDomain
            }
            else -> {
                DEFAULT_DOMAIN
            }
        }

        val configs = Configuration.Builder(requireContext(), notEmptyDomain, endpoint)
            .setDeviceUuid(deviceId)
            .setInstallationId(installId)
            .subscribeCustomerIfCreated(subscribeValue)
            .build()

        try {
            Mindbox.init(this.requireContext(), configs)

            loadProgress.visibility = View.GONE

            callback.invoke(
                InitializeData(
                    notEmptyDomain,
                    endpoint,
                    deviceId,
                    installId,
                    subscribeValue
                )
            )

        } catch (e: InitializeMindboxException) {
            loadProgress.visibility = View.GONE
            activity?.runOnUiThread {
                errorContainer.text = e.message
            }
        }
    }
}