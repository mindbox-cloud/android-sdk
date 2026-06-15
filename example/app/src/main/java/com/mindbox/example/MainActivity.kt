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
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import cloud.mindbox.mobile_sdk.Mindbox
import com.mindbox.example.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding: ActivityMainBinding
        get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        showSdkDataOnScreen()
        setupCopyOnClick()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkAndRequestPostNotificationsPermission()
        }

        processMindboxIntent(intent = intent, context = this)?.let { (url, payload) ->
            binding.tvPushUrlResult.text = url
            binding.tvPushPayloadResult.text = payload
            proceedUrl(url = url)
        }

        binding.btnShowInapp.setOnClickListener {
            InAppBottomSheet().show(supportFragmentManager, InAppBottomSheet::class.java.simpleName)
        }

        binding.btnAsyncOperation.setOnClickListener {
            //https://developers.mindbox.ru/docs/android-integration-of-actions
            sendAsync(type = AsyncOperationType.OPERATION_BODY_JSON, context = this)
            showToast(context = this, message = "Operation was sent")
        }

        binding.btnSyncOperation.setOnClickListener {
            //https://developers.mindbox.ru/docs/android-integration-of-actions
            sendSync(type = SyncOperationType.OPERATION_BODY_WITH_CUSTOM_RESPONSE, context = this)
            showToast(context = this, message = "Sync operation was sent")
        }

        binding.btnOpenActivity.setOnClickListener {
            startActivity(Intent(this, ActivityTransitionByPush::class.java))
        }

        binding.btnOpenPushList.setOnClickListener {
            startActivity(Intent(this, NotificationHistoryActivity::class.java))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processMindboxIntent(intent = intent, context = this)?.let { (url, payload) ->
            binding.tvPushUrlResult.text = url
            binding.tvPushPayloadResult.text = payload
            proceedUrl(url = url)
        }
        Mindbox.onNewIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    //https://developers.mindbox.ru/docs/android-sdk-methods#updatenotificationpermissionstatus-since-281
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

    //navigation to fragments after click on push. Check url and open the required fragment
    private fun proceedUrl(url: String?) {
        if (url == "https://gotofragment.com") {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, FragmentForNavigation())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun showSdkDataOnScreen() {
        //https://developers.mindbox.ru/docs/android-sdk-methods#subscribedeviceuuid-%D0%B8-disposedeviceuuidsubscription
        var subscriptionDeviceUuid = ""
        subscriptionDeviceUuid = Mindbox.subscribeDeviceUuid { deviceUUID ->
            runOnUiThread {
                binding.tvDeviceUUIDResult.text = deviceUUID
            }
            Mindbox.disposeDeviceUuidSubscription(subscriptionDeviceUuid)
        }

        //https://developers.mindbox.ru/docs/android-sdk-methods#subscribepushtoken-%D0%B8-disposepushtokensubscription
        var subscriptionPushToken = ""
        subscriptionPushToken = Mindbox.subscribePushTokens { tokens ->
            runOnUiThread {
                binding.tvTokenResult.text = tokens
                //https://developers.mindbox.ru/docs/android-sdk-methods#getpushtokensavedate
                binding.tvTokenDateResult.text = Mindbox.getPushTokensSaveDate().toString()
            }
            Mindbox.disposePushTokenSubscription(subscriptionPushToken)
        }

        //https://developers.mindbox.ru/docs/android-sdk-methods#getsdkversion
        binding.tvSdkVersionResult.text = Mindbox.getSdkVersion()
    }

    private fun setupCopyOnClick() {
        binding.rowDeviceUuid.copyOnClick(binding.tvDeviceUUIDResult)
        binding.rowToken.copyOnClick(binding.tvTokenResult)
        binding.rowTokenDate.copyOnClick(binding.tvTokenDateResult)
        binding.rowPushUrl.copyOnClick(binding.tvPushUrlResult)
        binding.rowPushPayload.copyOnClick(binding.tvPushPayloadResult)
        binding.rowSdkVersion.copyOnClick(binding.tvSdkVersionResult)
    }

    private fun View.copyOnClick(valueView: TextView) {
        setOnClickListener {
            val text = valueView.text.toString()
            if (text.isEmpty()) return@setOnClickListener
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
            // Android 13+ shows its own copy confirmation bubble — avoid double toast
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                showToast(context = this@MainActivity, message = "Скопировано")
            }
        }
    }
}
