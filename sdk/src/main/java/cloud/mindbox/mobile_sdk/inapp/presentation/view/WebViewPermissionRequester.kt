package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.data.managers.PermissionManagerImpl
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionStatus
import cloud.mindbox.mobile_sdk.inapp.presentation.actions.PushActivationActivity
import cloud.mindbox.mobile_sdk.inapp.presentation.actions.RuntimePermissionRequestBridge
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val PERMISSION_PAYLOAD_TYPE_FIELD_NAME = "type"

internal interface WebViewPermissionRequester {
    suspend fun requestPermission(activity: Activity, permissionType: PermissionType): PermissionActionResponse
}

internal enum class PermissionType(val value: String) {
    PUSH_NOTIFICATIONS("pushNotifications")
}

internal data class PermissionActionResponse(
    @SerializedName("result")
    val result: PermissionRequestStatus,
    @SerializedName("dialogShown")
    val dialogShown: Boolean,
    @SerializedName("details")
    val details: PermissionActionDetails,
)

internal data class PermissionActionDetails(
    @SerializedName("required")
    val required: Boolean,
    @SerializedName("shouldShowRequestPermissionRationale")
    val shouldShowRequestPermissionRationale: Boolean? = null,
)

internal enum class PermissionRequestStatus(val value: String) {
    @SerializedName("granted")
    GRANTED("granted"),

    @SerializedName("denied")
    DENIED("denied")
}

@SuppressLint("InlinedApi")
internal class WebViewPermissionRequesterImpl(
    private val context: Context,
    private val pushPermissionLauncher: PushPermissionLauncher = PushPermissionLauncherImpl(),
    private val permissionManager: PermissionManager = PermissionManagerImpl(context),
    private val sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT },
) : WebViewPermissionRequester {

    override suspend fun requestPermission(
        activity: Activity,
        permissionType: PermissionType
    ): PermissionActionResponse {
        val currentStatus: PermissionStatus = getPermissionStatus(permissionType)
        if (isGrantedStatus(currentStatus)) {
            return createPermissionActionResponse(
                result = PermissionRequestStatus.GRANTED,
                dialogShown = false
            )
        }
        if (!isPermissionRequired()) {
            return createPermissionActionResponse(
                result = PermissionRequestStatus.DENIED,
                dialogShown = false
            )
        }

        val permissionResult: PushPermissionRequestResult = pushPermissionLauncher.requestPermission(activity = activity)
        return createPermissionActionResponse(
            result = permissionResult.status,
            dialogShown = true,
            shouldShowRequestPermissionRationale = permissionResult.shouldShowRequestPermissionRationale
        )
    }

    private fun getPermissionStatus(permissionType: PermissionType): PermissionStatus {
        return when (permissionType) {
            PermissionType.PUSH_NOTIFICATIONS -> permissionManager.getNotificationPermissionStatus()
        }
    }

    private fun isGrantedStatus(permissionStatus: PermissionStatus): Boolean {
        return permissionStatus == PermissionStatus.GRANTED || permissionStatus == PermissionStatus.LIMITED
    }

    private fun isPermissionRequired(): Boolean = sdkIntProvider() >= Build.VERSION_CODES.TIRAMISU

    private fun createPermissionActionResponse(
        result: PermissionRequestStatus,
        dialogShown: Boolean,
        shouldShowRequestPermissionRationale: Boolean? = null
    ): PermissionActionResponse {
        val isPermissionRequired: Boolean = isPermissionRequired()
        return PermissionActionResponse(
            result = result,
            dialogShown = dialogShown,
            details = PermissionActionDetails(
                required = isPermissionRequired,
                shouldShowRequestPermissionRationale = shouldShowRequestPermissionRationale
            )
        )
    }
}

internal interface PushPermissionLauncher {
    suspend fun requestPermission(activity: Activity): PushPermissionRequestResult
}

internal data class PushPermissionRequestResult(
    val status: PermissionRequestStatus,
    val shouldShowRequestPermissionRationale: Boolean,
)

@SuppressLint("InlinedApi")
internal class PushPermissionLauncherImpl(
    private val sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT }
) : PushPermissionLauncher {
    private val notificationPermission: String = Manifest.permission.POST_NOTIFICATIONS

    override suspend fun requestPermission(activity: Activity): PushPermissionRequestResult {
        if (sdkIntProvider() < Build.VERSION_CODES.TIRAMISU) {
            return PushPermissionRequestResult(
                status = PermissionRequestStatus.DENIED,
                shouldShowRequestPermissionRationale = false
            )
        }
        val requestId: String = Mindbox.generateRandomUuid()
        val deferredResult = RuntimePermissionRequestBridge.register(requestId)
        withContext(Dispatchers.Main.immediate) {
            activity.startActivity(
                Intent(activity, PushActivationActivity::class.java).apply {
                    putExtra(PushActivationActivity.EXTRA_REQUEST_ID, requestId)
                }
            )
        }
        val isGranted: Boolean = deferredResult.await()
        val shouldShowRationale: Boolean = withContext(Dispatchers.Main.immediate) {
            activity.shouldShowRequestPermissionRationale(notificationPermission)
        }
        return if (isGranted) {
            PushPermissionRequestResult(
                status = PermissionRequestStatus.GRANTED,
                shouldShowRequestPermissionRationale = shouldShowRationale
            )
        } else {
            PushPermissionRequestResult(
                status = PermissionRequestStatus.DENIED,
                shouldShowRequestPermissionRationale = shouldShowRationale
            )
        }
    }
}
