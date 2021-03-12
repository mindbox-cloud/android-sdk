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
        val enteringDataFragment = EnteringDataFragment()
        enteringDataFragment.callback = { data ->
            openCompleteScreen(data)
        }

        supportFragmentManager.beginTransaction()
            .replace(
                R.id.fragmentContainer,
                enteringDataFragment
            )
            .commit()
    }

    private fun openCompleteScreen(data: InitializeData) {
        val dataFragment = CompleteDataFragment()
        dataFragment.data = data
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.fragmentContainer,
                dataFragment
            )
            .addToBackStack(CompleteDataFragment::class.java.simpleName)
            .commit()
    }
}