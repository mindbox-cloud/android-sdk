package com.mindbox.example

import android.Manifest
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import cloud.mindbox.mobile_sdk.Mindbox
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mindbox.example.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding: ActivityMainBinding
        get() = _binding!!

    private var inAppLayoutView: View? = null
    private var inAppBackgroundView: View? = null
    private var inAppOriginalParent: ViewGroup? = null
    private var inAppLayoutOriginalIndex: Int = -1
    private var inAppBackgroundOriginalIndex: Int = -1
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            repositionInApp()
        }
        window.decorView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)

        showSdkDataOnScreen()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkAndRequestPostNotificationsPermission()
        }

        processMindboxIntent(intent = intent, context = this)?.let { (url, payload) ->
            binding.tvPushUrlResult.text = url
            binding.tvPushPayloadResult.text = payload
            proceedUrl(url = url)
        }

        binding.btnAsyncOperation.setOnClickListener {
            //https://developers.mindbox.ru/docs/android-integration-of-actions
            sendAsync(type = AsyncOperationType.OPERATION_BODY_JSON, context = this)
            showToast(context = this, message = "Operation was sent")
        }

        binding.btnSyncOperation.setOnClickListener {
            //https://developers.mindbox.ru/docs/android-integration-of-actions
            sendSync(type = SyncOperationType.OPERATION_BODY_WITH_CUSTOM_RESPONSE, context = this)
        }
        binding.btnOpenActivity.setOnClickListener {
            val intent = Intent(this, ActivityTransitionByPush::class.java)
            this.startActivity(intent)
        }

        binding.btnOpenPushList.setOnClickListener {
            startActivity(Intent(this, NotificationHistoryActivity::class.java))
        }

        val bottomSheet = DemoBottomSheet()
        bottomSheet.show(supportFragmentManager, "BottomSheetDialogFragment")
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
        window.decorView.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
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
        subscriptionPushToken =
            Mindbox.subscribePushTokens { tokens ->
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

    class DemoBottomSheet : BottomSheetDialogFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
        }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            return inflater.inflate(
                R.layout.activity_test,
                container,
                false
            )
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            view.post {
                val parent = view.parent as View
                val params = parent.layoutParams
                val behavior = (params as ViewGroup.LayoutParams).apply {
                    height = (resources.displayMetrics.heightPixels * 0.5).toInt()
                }
                parent.layoutParams = behavior
            }
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val bottomSheetDialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
            bottomSheetDialog.setOnShowListener { dialog: DialogInterface? ->
                val bottomSheet = (dialog as BottomSheetDialog)
                    .findViewById<FrameLayout?>(com.google.android.material.R.id.design_bottom_sheet)
                if (bottomSheet != null) BottomSheetBehavior
                    .from<FrameLayout?>(bottomSheet)
                    .setState(BottomSheetBehavior.STATE_EXPANDED)
            }
            return bottomSheetDialog
        }
    }

    private fun repositionInApp() {
        val rootView = window.decorView.rootView
        // Always re-scan for the views
        inAppLayoutView = findViewInHierarchy(rootView, R.id.inapp_layout)
        inAppBackgroundView = findViewInHierarchy(rootView, R.id.inapp_background_layout)

        if (inAppLayoutView == null && inAppBackgroundView == null) {
            inAppOriginalParent = null
            return
        }

        // Step 2: Find the top-most visible DialogFragment.
        val topDialog = supportFragmentManager.fragments
            .filterIsInstance<DialogFragment>()
            .lastOrNull { it.isAdded && it.dialog?.isShowing == true }

        // Step 3: Determine the target parent for the In-App view.
        val targetParent = topDialog?.dialog?.window?.decorView as? ViewGroup

        // Case 1: No dialog is visible. The target is the original parent.
        if (targetParent == null) {
            if (inAppOriginalParent != null) {
                // The views should be moved back to where they came from.
                inAppBackgroundView?.let {
                    if (it.parent != inAppOriginalParent) {
                        (it.parent as? ViewGroup)?.removeView(it)
                        inAppOriginalParent?.addView(it, inAppBackgroundOriginalIndex)
                    }
                }
                inAppLayoutView?.let {
                    if (it.parent != inAppOriginalParent) {
                        (it.parent as? ViewGroup)?.removeView(it)
                        inAppOriginalParent?.addView(it, inAppLayoutOriginalIndex)
                    }
                }
                // Reset the state now that they are home.
                inAppOriginalParent = null
            }
            return
        }

        // Case 2: A dialog is visible. This is our target.
        // We assume both views share the same parent.
        val currentParent = (inAppLayoutView?.parent ?: inAppBackgroundView?.parent) as? ViewGroup
        if (currentParent != targetParent) {
            // If this is the first time we're moving the views, save their original parent and position.
            if (inAppOriginalParent == null && currentParent != null) {
                inAppOriginalParent = currentParent
                inAppLayoutOriginalIndex = inAppLayoutView?.let(currentParent::indexOfChild) ?: -1
                inAppBackgroundOriginalIndex = inAppBackgroundView?.let(currentParent::indexOfChild) ?: -1
            }

            // Move the views to the target parent.
            inAppBackgroundView?.let {
                (it.parent as? ViewGroup)?.removeView(it)
                targetParent.addView(it)
            }
            inAppLayoutView?.let {
                (it.parent as? ViewGroup)?.removeView(it)
                targetParent.addView(it)
            }
        }
    }

    private fun findViewInHierarchy(parent: View, id: Int): View? {
        if (parent.id == id) {
            return parent
        }
        if (parent is ViewGroup) {
            for (i in 0 until parent.childCount) {
                val found = findViewInHierarchy(parent.getChildAt(i), id)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }
}
