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
                EnteringDataFragment { data ->
                    openCompleteScreen(data)
                })
            .commit()
    }

    private fun openCompleteScreen(data: InitializeData) {
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.fragmentContainer,
                CompleteDataFragment(data)
            )
            .addToBackStack(CompleteDataFragment::class.java.simpleName)
            .commit()
    }
}