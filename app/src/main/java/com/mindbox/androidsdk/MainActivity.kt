package com.mindbox.androidsdk

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cloud.mindbox.mobile_sdk.Configuration
import cloud.mindbox.mobile_sdk.Mindbox
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(R.layout.activity_main) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Mindbox.init(this, Configuration()) { fb, device ->
            runOnUiThread {
                textViewMain.text = device
                textViewFirebase.text = fb
            }
        }
    }
}