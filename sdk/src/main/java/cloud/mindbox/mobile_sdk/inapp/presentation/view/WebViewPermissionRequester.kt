package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import cloud.mindbox.mobile_sdk.inapp.data.managers.PermissionManagerImpl
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionStatus
import cloud.mindbox.mobile_sdk.inapp.presentation.actions.RuntimePermissionRequestActivity
import cloud.mindbox.mobile_sdk.inapp.presentation.actions.RuntimePermissionRequestBridge
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

internal interface WebViewPermissionRequester {
    suspend fun requestPermission(activity: Activity, permissionType: PermissionType): PermissionActionResponse
}

internal enum class PermissionType(val value: String) {
    PUSH_NOTIFICATIONS("pushNotifications"),
    LOCATION("location"),
    CAMERA("camera"),
    MICROPHONE("microphone"),
    PHOTO_LIBRARY("photoLibrary")
}

internal data class PermissionActionResponse(
    @SerializedName("result")
    val result: PermissionRequestStatus,
    @SerializedName("dialogShown")
    val dialogShown: Boolean,
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
    private val runtimePermissionLauncher: RuntimePermissionLauncher = RuntimePermissionLauncherImpl(),
    private val manifestPermissionChecker: PermissionManifestChecker = ManifestPermissionChecker(context),
    private val permissionManager: PermissionManager = PermissionManagerImpl(context),
    private val sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT },
) : WebViewPermissionRequester {

    override suspend fun requestPermission(
        activity: Activity,
        permissionType: PermissionType
    ): PermissionActionResponse {
        val currentStatus: PermissionStatus = getPermissionStatus(permissionType)
        if (isGrantedStatus(currentStatus)) {
            return PermissionActionResponse(
                result = PermissionRequestStatus.GRANTED,
                dialogShown = false
            )
        }
        val permissionsToRequest: List<String> = resolveRequestPermissions(permissionType)
        if (permissionsToRequest.isEmpty()) {
            return PermissionActionResponse(
                result = PermissionRequestStatus.DENIED,
                dialogShown = false
            )
        }
        val declaredPermissions: List<String> = permissionsToRequest.filter { permission: String ->
            manifestPermissionChecker.isPermissionDeclared(permission)
        }
        if (declaredPermissions.isEmpty()) {
            throw IllegalStateException("Permission is not declared in AndroidManifest for type: ${permissionType.value}")
        }
        declaredPermissions.forEach { permission: String ->
            val status: PermissionRequestStatus = runtimePermissionLauncher.requestPermission(
                activity = activity,
                permissions = arrayOf(permission)
            )
            if (status == PermissionRequestStatus.GRANTED) {
                return PermissionActionResponse(
                    result = status,
                    dialogShown = true
                )
            }
        }
        return PermissionActionResponse(
            result = PermissionRequestStatus.DENIED,
            dialogShown = true
        )
    }

    private fun getPermissionStatus(permissionType: PermissionType): PermissionStatus {
        return when (permissionType) {
            PermissionType.PUSH_NOTIFICATIONS -> permissionManager.getNotificationPermissionStatus()
            PermissionType.LOCATION -> permissionManager.getLocationPermissionStatus()
            PermissionType.CAMERA -> permissionManager.getCameraPermissionStatus()
            PermissionType.MICROPHONE -> permissionManager.getMicrophonePermissionStatus()
            PermissionType.PHOTO_LIBRARY -> permissionManager.getPhotoLibraryPermissionStatus()
        }
    }

    private fun isGrantedStatus(permissionStatus: PermissionStatus): Boolean {
        return permissionStatus == PermissionStatus.GRANTED || permissionStatus == PermissionStatus.LIMITED
    }

    private fun resolveRequestPermissions(permissionType: PermissionType): List<String> {
        return when (permissionType) {
            PermissionType.PUSH_NOTIFICATIONS -> {
                if (sdkIntProvider() >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    emptyList()
                }
            }
            PermissionType.LOCATION -> listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            PermissionType.CAMERA -> listOf(Manifest.permission.CAMERA)
            PermissionType.MICROPHONE -> listOf(Manifest.permission.RECORD_AUDIO)
            PermissionType.PHOTO_LIBRARY -> resolveLibraryPermissions()
        }
    }

    private fun resolveLibraryPermissions(): List<String> {
        if (sdkIntProvider() >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
        }
        if (sdkIntProvider() >= Build.VERSION_CODES.TIRAMISU) {
            return listOf(Manifest.permission.READ_MEDIA_IMAGES)
        }
        return listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

internal interface RuntimePermissionLauncher {
    suspend fun requestPermission(activity: Activity, permissions: Array<String>): PermissionRequestStatus
}

internal class RuntimePermissionLauncherImpl : RuntimePermissionLauncher {
    override suspend fun requestPermission(
        activity: Activity,
        permissions: Array<String>
    ): PermissionRequestStatus {
        val requestId: String = UUID.randomUUID().toString()
        val deferredResult = RuntimePermissionRequestBridge.register(requestId)
        withContext(Dispatchers.Main.immediate) {
            activity.startActivity(
                Intent(activity, RuntimePermissionRequestActivity::class.java).apply {
                    putExtra(RuntimePermissionRequestActivity.EXTRA_REQUEST_ID, requestId)
                    putExtra(RuntimePermissionRequestActivity.EXTRA_PERMISSIONS, permissions)
                }
            )
        }
        val isGranted: Boolean = deferredResult.await()
        return if (isGranted) {
            PermissionRequestStatus.GRANTED
        } else {
            PermissionRequestStatus.DENIED
        }
    }
}

internal interface PermissionManifestChecker {
    fun isPermissionDeclared(permission: String): Boolean
}

internal class ManifestPermissionChecker(
    private val context: Context
) : PermissionManifestChecker {
    private val declaredPermissions: Set<String> by lazy {
        readDeclaredPermissions()
    }

    override fun isPermissionDeclared(permission: String): Boolean {
        return declaredPermissions.contains(permission)
    }

    private fun readDeclaredPermissions(): Set<String> {
        val packageInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        }
        return packageInfo.requestedPermissions?.toSet().orEmpty()
    }
}
