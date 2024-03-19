package cloud.mindbox.mobile_sdk.inapp.presentation.actions

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.utils.Constants

internal class PushActivationActivity : AppCompatActivity() {

    private val mindboxNotificationManager by mindboxInject { mindboxNotificationManager }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 125129
    }

    /* @RequiresApi(Build.VERSION_CODES.M)
     private val requestPermissionLauncher =
         registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
             if (!isGranted && shouldShowRequestPermissionRationale(Constants.POST_NOTIFICATION)) {
                 mindboxLogI("User reject first permission request")
             } else if (!isGranted && !shouldShowRequestPermissionRationale(Constants.POST_NOTIFICATION)) {
                 if (mindboxNotificationManager.shouldOpenSettings) {
                     mindboxLogI("User already rejected permission two time, try open settings")
                     mindboxNotificationManager.openNotificationSettings(this)
                 } else {
                     mindboxNotificationManager.shouldOpenSettings = true
                 }
             } else if (isGranted) {
                 mindboxLogI("User click 'allow' in request permission")
                 Mindbox.updateNotificationPermissionStatus(this)
             }
             finish()
         }*/
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    mindboxLogI("User clicked 'allow' in request permission")
                    Mindbox.updateNotificationPermissionStatus(this)
                }
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_DENIED && !shouldShowRequestPermissionRationale(
                        Constants.POST_NOTIFICATION
                    )
                ) {
                    if (mindboxNotificationManager.shouldOpenSettings) {
                        mindboxLogI("User already rejected permission two times, try open settings")
                        mindboxNotificationManager.openNotificationSettings(this)
                    } else {
                        mindboxNotificationManager.shouldOpenSettings = true
                    }
                }
                if (grantResults.isEmpty()) {
                    mindboxLogI("User dismiss request permission")
                }
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED && shouldShowRequestPermissionRationale(Constants.POST_NOTIFICATION)) {
                    mindboxLogI("User reject first permission request")

                }
                finish()
            }

        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setLayout(0, 0)
        mindboxLogD("Call permission laucher")
        requestPermissions(arrayOf(Constants.POST_NOTIFICATION), PERMISSION_REQUEST_CODE)
    }
}