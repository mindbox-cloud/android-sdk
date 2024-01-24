package com.mindbox.example

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cloud.mindbox.mobile_sdk.Mindbox
import com.mindbox.example.databinding.ActivityTrasitionByPushBinding


class ActivityTransitionByPush : AppCompatActivity() {
    private lateinit var binding: ActivityTrasitionByPushBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrasitionByPushBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Get data from push after click on push or button in push
        processMindboxIntent(intent, this)?.let { (url, payload) ->
            binding.tvPushUrlResultSecondActivity.text = url
            binding.tvPushPayloadResultSecondActivity.text = payload
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        //Get data from push after click on push or button in push
        processMindboxIntent(intent, this)?.let { (url, payload) ->
            binding.tvPushUrlResultSecondActivity.text = url
            binding.tvPushPayloadResultSecondActivity.text = payload
        }
        //https://developers.mindbox.ru/docs/android-app-start-tracking
        Mindbox.onNewIntent(intent)
    }
}