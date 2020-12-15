package com.mindbox.androidsdk

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import cloud.mindbox.mobile_sdk.Mindbox
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(R.layout.activity_main) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Mindbox.getDeviceUuid {
            runOnUiThread {
                textViewMain.text = it
            }
            Log.i("MainActivity", "get device uuid: $it")
        }

        Mindbox.getFirebaseToken {
            runOnUiThread {
                textViewFirebase.text = it
            }
        }
    }
}