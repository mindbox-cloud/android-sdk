package com.mindbox.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import cloud.mindbox.mobile_sdk.Mindbox
import com.google.gson.Gson
import com.mindbox.example.ui.NotificationHistoryScreen
import com.mindbox.example.ui.theme.MindboxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationHistoryActivity : AppCompatActivity() {

    private var notifications by mutableStateOf(NotificationStorage.notifications)

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
        Mindbox.executeAsyncOperation(
            applicationContext,
            "mobileapp.NCOpen",
            ""
        )
        Toast.makeText(
            applicationContext,
            getString(R.string.toast_notification_center_opened),
            Toast.LENGTH_LONG
        ).show()

        setContent {
            MindboxTheme {
                NotificationHistoryScreen(
                    notifications = notifications,
                    darkTheme = isSystemInDarkTheme(),
                    onBack = { finish() },
                    onItemClick = ::onNotificationClick,
                )
            }
        }

        // Don't listen to storage in your actual app inside activity.
        lifecycleScope.launch(Dispatchers.IO) {
            NotificationStorage.notificationsFlow.collect { notifications = it }
        }
    }

    private fun onNotificationClick(item: cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage) {
        /*Assuming payload of push notification has this structure:
             {
                "pushName":"<Push name>",
                "pushDate":"<Push date>"
              }*/
        val pushPayload = Gson().fromJson(item.payload, PushPayload::class.java)
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
            getString(R.string.toast_notification_click, item.uniqueKey, item.title, item.description),
            Toast.LENGTH_LONG
        ).show()
    }
}
