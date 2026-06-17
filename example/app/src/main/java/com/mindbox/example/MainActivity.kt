package com.mindbox.example

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import cloud.mindbox.mobile_sdk.Mindbox
import com.mindbox.example.ui.InAppOption
import com.mindbox.example.ui.MainScreen
import com.mindbox.example.ui.SdkInfoState
import com.mindbox.example.ui.theme.MindboxTheme

class MainActivity : AppCompatActivity() {

    private var sdkInfo by mutableStateOf(SdkInfoState())
    private var showInAppSheet by mutableStateOf(false)
    private var showNavFragment by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        showSdkDataOnScreen()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkAndRequestPostNotificationsPermission()
        }

        handlePushIntent(intent)

        setContent {
            MindboxTheme {
                val darkTheme = isSystemInDarkTheme()
                Box(Modifier.fillMaxSize()) {
                    MainScreen(
                        state = sdkInfo,
                        darkTheme = darkTheme,
                        onCopy = ::copyToClipboard,
                        onShowInApp = { showInAppSheet = true },
                        onSendAsync = {
                            // https://developers.mindbox.ru/docs/android-integration-of-actions
                            sendAsync(type = AsyncOperationType.OPERATION_BODY_JSON, context = this@MainActivity)
                            showToast(this@MainActivity, getString(R.string.toast_operation_sent))
                        },
                        onSendSync = {
                            // https://developers.mindbox.ru/docs/android-integration-of-actions
                            sendSync(type = SyncOperationType.OPERATION_BODY_WITH_CUSTOM_RESPONSE, context = this@MainActivity)
                            showToast(this@MainActivity, getString(R.string.toast_sync_operation_sent))
                        },
                        onOpenSecondActivity = {
                            startActivity(Intent(this@MainActivity, ActivityTransitionByPush::class.java))
                        },
                        onOpenHistory = {
                            startActivity(Intent(this@MainActivity, NotificationHistoryActivity::class.java))
                        },
                        showInAppSheet = showInAppSheet,
                        onDismissInAppSheet = { showInAppSheet = false },
                        onPickInApp = { option ->
                            showInAppSheet = false
                            // https://developers.mindbox.ru/docs/android-integration-of-actions
                            when (option) {
                                InAppOption.WheelOfFortune ->
                                    sendAsyncOperationWithEmptyBody(this@MainActivity, "Test1")
                                InAppOption.LuckFeed ->
                                    sendAsyncOperationWithEmptyBody(this@MainActivity, "Test2")
                                InAppOption.ScratchCard -> Unit
                            }
                            showToast(
                                this@MainActivity,
                                getString(R.string.toast_inapp_shown, getString(option.titleRes)),
                            )
                        },
                    )

                    if (showNavFragment) {
                        NavFragmentOverlay(darkTheme = darkTheme) { showNavFragment = false }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePushIntent(intent)
        Mindbox.onNewIntent(intent)
    }

    private fun handlePushIntent(intent: Intent?) {
        processMindboxIntent(intent = intent, context = this)?.let { (url, payload) ->
            sdkInfo = sdkInfo.copy(pushUrl = url.orEmpty(), pushPayload = payload.orEmpty())
            proceedUrl(url = url)
        }
    }

    // https://developers.mindbox.ru/docs/android-sdk-methods#updatenotificationpermissionstatus-since-281
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Mindbox.updateNotificationPermissionStatus(context = this)
            } else {
                Log.d(Utils.TAG, "Notification permission not allowed")
            }
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkAndRequestPostNotificationsPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Log.d(Utils.TAG, "Already granted")
        }
    }

    // navigation after click on push: check url and show the in-app fragment screen
    private fun proceedUrl(url: String?) {
        if (url == "https://gotofragment.com") {
            showNavFragment = true
        }
    }

    private fun showSdkDataOnScreen() {
        // https://developers.mindbox.ru/docs/android-sdk-methods#subscribedeviceuuid-%D0%B8-disposedeviceuuidsubscription
        var subscriptionDeviceUuid = ""
        subscriptionDeviceUuid = Mindbox.subscribeDeviceUuid { deviceUUID ->
            runOnUiThread { sdkInfo = sdkInfo.copy(deviceUuid = deviceUUID) }
            Mindbox.disposeDeviceUuidSubscription(subscriptionDeviceUuid)
        }

        // https://developers.mindbox.ru/docs/android-sdk-methods#subscribepushtoken-%D0%B8-disposepushtokensubscription
        var subscriptionPushToken = ""
        subscriptionPushToken = Mindbox.subscribePushTokens { tokens ->
            runOnUiThread {
                sdkInfo = sdkInfo.copy(
                    token = tokens.orEmpty(),
                    // https://developers.mindbox.ru/docs/android-sdk-methods#getpushtokensavedate
                    tokenDate = Mindbox.getPushTokensSaveDate().toString(),
                )
            }
            Mindbox.disposePushTokenSubscription(subscriptionPushToken)
        }

        // https://developers.mindbox.ru/docs/android-sdk-methods#getsdkversion
        sdkInfo = sdkInfo.copy(sdkVersion = Mindbox.getSdkVersion())
    }

    private fun copyToClipboard(label: String, value: String) {
        if (value.isEmpty()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        // Android 13+ shows its own copy confirmation bubble — avoid double toast
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            showToast(this, getString(R.string.toast_copied))
        }
    }
}

/** Replaces the old FragmentForNavigation — shown when a push deep-links to gotofragment.com. */
@androidx.compose.runtime.Composable
private fun NavFragmentOverlay(darkTheme: Boolean, onBack: () -> Unit) {
    com.mindbox.example.ui.theme.SetStatusBarAppearance(darkIcons = !darkTheme)
    androidx.activity.compose.BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            androidx.compose.ui.res.stringResource(R.string.nav_fragment_message),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
