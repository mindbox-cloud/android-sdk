package cloud.mindbox.mobile_sdk.inapp.presentation.actions

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.utils.Constants

internal class PushActivationActivity : Activity() {

    private val mindboxNotificationManager by mindboxInject { mindboxNotificationManager }
    private val requestPermissionManager by mindboxInject { requestPermissionManager }
    private var shouldCheckDialogShowing = false
    private val resumeTimes = mutableListOf<Long>()

    companion object {
        private const val PERMISSION_REQUEST_CODE = 125129
        private const val TIME_BETWEEN_RESUME = 700
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != PERMISSION_REQUEST_CODE || grantResults.isEmpty()) return

        val granted = grantResults[0] == PackageManager.PERMISSION_GRANTED
        val shouldShowRationale = shouldShowRequestPermissionRationale(Constants.POST_NOTIFICATION)
        val permissionDenied = grantResults[0] == PackageManager.PERMISSION_DENIED

        when {
            granted -> {
                mindboxLogI("User clicked 'allow' in request permission")
                Mindbox.updateNotificationPermissionStatus(this)
                finish()
            }

            permissionDenied && !shouldShowRationale -> {
                if (mindboxNotificationManager.shouldOpenSettings) {
                    if (requestPermissionManager.getRequestCount() > 1) {
                        mindboxLogI("User already rejected permission two times, try open settings")
                        mindboxNotificationManager.openNotificationSettings(this)
                        finish()
                    } else {
                        mindboxLogI("Awaiting show dialog")
                        shouldCheckDialogShowing = true
                    }
                } else {
                    mindboxNotificationManager.shouldOpenSettings = true
                    finish()
                }
            }

            permissionDenied && shouldShowRationale -> {
                mindboxLogI("User rejected first permission request")
                finish()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.isClickable = false
        window.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        mindboxLogI("Call permission laucher")
        requestPermissions(arrayOf(Constants.POST_NOTIFICATION), PERMISSION_REQUEST_CODE)
    }

    override fun onResume() {
        resumeTimes.add(SystemClock.elapsedRealtime())
        if (shouldCheckDialogShowing) {
            val duration = resumeTimes.last() - resumeTimes.first()
            if (duration < TIME_BETWEEN_RESUME) {
                resumeTimes.clear()
                mindboxLogI("System dialog not shown because timeout=$duration -> open settings")
                mindboxNotificationManager.openNotificationSettings(this)
            } else {
                mindboxLogI("User dismiss permission request because timeout=$duration")
                requestPermissionManager.decreaseRequestCounter()
            }
            shouldCheckDialogShowing = false
            finish()
        }
        super.onResume()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            finish()
            return true
        }
        return super.onTouchEvent(event)
    }
}
