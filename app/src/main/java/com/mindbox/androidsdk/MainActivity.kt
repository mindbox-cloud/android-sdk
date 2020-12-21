package com.mindbox.androidsdk

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mindbox.androidsdk.screens.EnteringDataFragment
import com.orhanobut.hawk.Hawk

class MainActivity : AppCompatActivity(R.layout.activity_main) {

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
                EnteringDataFragment {

                })
            .commit()
    }
}