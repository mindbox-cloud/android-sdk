package com.mindbox.androidsdk

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mindbox.androidsdk.screens.CompleteDataFragment
import com.mindbox.androidsdk.screens.EnteringDataFragment
import com.orhanobut.hawk.Hawk

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Hawk.init(this).build()

        openEnteringDataScreen()
    }

    private fun openEnteringDataScreen() {
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.fragmentContainer,
                EnteringDataFragment { domain, endpoint, deviceId, installId ->
                    openCompleteScreen(domain, endpoint, deviceId, installId)
                })
            .commit()
    }

    private fun openCompleteScreen(domain: String, endpoint: String, deviceId: String, installId: String) {
        supportFragmentManager.beginTransaction()
            .add(
                R.id.fragmentContainer,
                CompleteDataFragment(domain, endpoint, deviceId, installId)
            )
            .addToBackStack(CompleteDataFragment::class.java.simpleName)
            .commit()
    }
}