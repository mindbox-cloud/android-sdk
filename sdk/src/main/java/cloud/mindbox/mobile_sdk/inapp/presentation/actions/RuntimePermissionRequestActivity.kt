package cloud.mindbox.mobile_sdk.inapp.presentation.actions

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import cloud.mindbox.mobile_sdk.logger.mindboxLogW

internal class RuntimePermissionRequestActivity : Activity() {

    companion object {
        private const val REQUEST_CODE: Int = 125130
        internal const val EXTRA_REQUEST_ID: String = "runtime_permission_request_id"
        internal const val EXTRA_PERMISSIONS: String = "runtime_permission_permissions"
    }

    private var requestId: String? = null
    private var isResultSent: Boolean = false

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window.decorView.setBackgroundColor(Color.TRANSPARENT)
        window.decorView.isClickable = false
        window.setDimAmount(0f)
        val actualRequestId: String = intent?.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        val permissions: Array<String> = intent?.getStringArrayExtra(EXTRA_PERMISSIONS)
            ?.map { permission: String -> permission }
            ?.toTypedArray()
            ?: emptyArray()
        if (actualRequestId.isBlank() || permissions.isEmpty()) {
            finish()
            return
        }
        requestId = actualRequestId
        requestPermissions(permissions, REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CODE) {
            return
        }
        val isGranted: Boolean = grantResults.isNotEmpty() && grantResults.all { result: Int ->
            result == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        val actualRequestId: String = requestId.orEmpty()
        if (actualRequestId.isNotBlank()) {
            RuntimePermissionRequestBridge.resolve(actualRequestId, isGranted)
            isResultSent = true
        }
        finish()
    }

    override fun onDestroy() {
        if (!isResultSent) {
            val actualRequestId: String = requestId.orEmpty()
            if (actualRequestId.isNotBlank()) {
                mindboxLogW("Permission request activity closed before result for id=$actualRequestId")
                RuntimePermissionRequestBridge.resolve(actualRequestId, false)
            }
        }
        super.onDestroy()
    }
}
