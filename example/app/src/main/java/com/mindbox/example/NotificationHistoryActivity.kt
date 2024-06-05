package com.mindbox.example

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import cloud.mindbox.mobile_sdk.Mindbox
import com.google.gson.Gson
import com.mindbox.example.databinding.ActivityNotificationHistoryActivivityBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationHistoryActivity : AppCompatActivity() {

    private var _binding: ActivityNotificationHistoryActivivityBinding? = null
    private val binding: ActivityNotificationHistoryActivivityBinding
        get() = _binding!!

    private fun getPushOpenOperationBody(
        pushName: String,
        pushDate: String
    ): String {
        return """{
            "data":{
            "customerAction": {
            "customFields": {
            "mobPushSendDateTime": "$pushDate",
            "mobPushTranslateName": "$pushName"
        }
        }
        }}""".trimIndent().replace("\n", "").filter { !it.isWhitespace() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityNotificationHistoryActivivityBinding.inflate(layoutInflater)
        Mindbox.executeAsyncOperation(
            applicationContext,
            "mobileapp.NCOpen",
            ""
        )
        setContentView(binding.root)
        binding.rvList.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(applicationContext)
            adapter = NotificationAdapter {
                /*Assuming payload of push notification has this structure:
                     {
                        "pushName":"<Push name>",
                        "pushDate":"<Push date>"
                      }*/
                val pushPayload = Gson().fromJson(it.payload, PushPayload::class.java)
                Mindbox.executeAsyncOperation(
                    applicationContext,
                    "mobileapp.NCPushOpen",
                    getPushOpenOperationBody(
                        pushPayload.pushName,
                        pushPayload.pushDate
                    )
                )
                Toast.makeText(
                    applicationContext,
                    "Click on notification with unique key ${it.uniqueKey}, title = ${it.title} and description = ${it.description}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        (binding.rvList.adapter as NotificationAdapter).updateNotifications(NotificationStorage.notifications)
        //Don't listen to storage in your actual app inside activity.
        lifecycleScope.launch(Dispatchers.IO) {
            NotificationStorage.notificationsFlow.collect {
                (binding.rvList.adapter as NotificationAdapter).updateNotifications(it)
            }
        }
    }
}