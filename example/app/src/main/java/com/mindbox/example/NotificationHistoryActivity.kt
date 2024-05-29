package com.mindbox.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import com.mindbox.example.databinding.ActivityNotificationHistoryActivivityBinding

class NotificationHistoryActivity : AppCompatActivity() {

    private var _binding: ActivityNotificationHistoryActivivityBinding? = null
    private val binding: ActivityNotificationHistoryActivivityBinding
        get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityNotificationHistoryActivivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.rvList.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(applicationContext)
            adapter = NotificationAdapter {
                Mindbox.executeAsyncOperation(applicationContext, "Send push click from notificaiton center", "")
            }
        }

        val testNotifications = listOf(
            MindboxRemoteMessage(
                "1",
                title = "First notification title",
                description = "First notification text",
                pushActions = emptyList(),
                "2",
                "testUrl",
                payload = ""
            ), MindboxRemoteMessage(
                "2",
                title = "Second notification title",
                description = "Second notification text",
                pushActions = emptyList(),
                "2",
                "testUrl",
                payload = ""
            ), MindboxRemoteMessage( "3",
                title = "Third notification title",
                description = "Third notification text",
                pushActions = emptyList(),
                "2",
                "testUrl",
                payload = "")
        )
        (binding.rvList.adapter as NotificationAdapter).updateNotifications(testNotifications)
    }
}