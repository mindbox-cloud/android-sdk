package cloud.mindbox.mobile_sdk.inapp.presentation.actions

import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.utils.Constants

class PushActivationActivity : AppCompatActivity() {

    private val mindboxNotificationManager by mindboxInject { mindboxNotificationManager }

    @RequiresApi(Build.VERSION_CODES.M)
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (!isGranted && shouldShowRequestPermissionRationale(Constants.POST_NOTIFICATION)) {
                mindboxLogI("User reject first permission request")
            } else if (!isGranted && !shouldShowRequestPermissionRationale(Constants.POST_NOTIFICATION)) {
                mindboxLogI("User rejected permission two time, try open settings")
                mindboxNotificationManager.callCallback()
            } else if (isGranted) {
                mindboxLogI("User click 'allow' in request permission")
            }
            finish()
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setLayout(0, 0)
        mindboxLogD("Call permission laucher")
        requestPermissionLauncher.launch(Constants.POST_NOTIFICATION)
    }
}