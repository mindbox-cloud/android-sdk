package com.mindbox.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cloud.mindbox.mobile_sdk.Mindbox
import com.mindbox.example.ui.SecondActivityScreen
import com.mindbox.example.ui.theme.MindboxTheme

class ActivityTransitionByPush : AppCompatActivity() {

    private var pushUrl by mutableStateOf("")
    private var pushPayload by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get data from push after click on push or button in push
        applyPushIntent(intent)

        setContent {
            MindboxTheme {
                SecondActivityScreen(
                    pushUrl = pushUrl,
                    pushPayload = pushPayload,
                    triggerUrl = SECOND_ACTIVITY_PUSH_URL,
                    darkTheme = isSystemInDarkTheme(),
                    onBack = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Get data from push after click on push or button in push
        applyPushIntent(intent)
        // https://developers.mindbox.ru/docs/android-app-start-tracking
        Mindbox.onNewIntent(intent)
    }

    private fun applyPushIntent(intent: Intent?) {
        processMindboxIntent(intent, this)?.let { (url, payload) ->
            pushUrl = url.orEmpty()
            pushPayload = payload.orEmpty()
        }
    }
}
