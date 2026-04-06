package cloud.mindbox.mobile_sdk.inapp.data.managers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionStatus
import cloud.mindbox.mobile_sdk.logger.mindboxLogE

internal class PermissionManagerImpl(private val context: Context) : PermissionManager {

    override fun getCameraPermissionStatus(): PermissionStatus {
        return runCatching {
            resolveRuntimePermissionStatus(Manifest.permission.CAMERA)
        }.getOrElse { _ ->
            mindboxLogE("Unknown error checking camera permission status")
            PermissionStatus.NOT_DETERMINED
        }
    }

    override fun getLocationPermissionStatus(): PermissionStatus {
        return runCatching {
            val fineStatus: PermissionStatus = resolveRuntimePermissionStatus(Manifest.permission.ACCESS_FINE_LOCATION)
            val coarseStatus: PermissionStatus = resolveRuntimePermissionStatus(Manifest.permission.ACCESS_COARSE_LOCATION)
            when {
                fineStatus == PermissionStatus.GRANTED || coarseStatus == PermissionStatus.GRANTED -> PermissionStatus.GRANTED
                else -> PermissionStatus.DENIED
            }
        }.getOrElse { _ ->
            mindboxLogE("Unknown error checking location permission status")
            PermissionStatus.NOT_DETERMINED
        }
    }

    override fun getMicrophonePermissionStatus(): PermissionStatus {
        return runCatching {
            resolveRuntimePermissionStatus(Manifest.permission.RECORD_AUDIO)
        }.getOrElse { _ ->
            mindboxLogE("Unknown error checking microphone permission status")
            PermissionStatus.NOT_DETERMINED
        }
    }

    override fun getNotificationPermissionStatus(): PermissionStatus {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val runtimeStatus: PermissionStatus = resolveRuntimePermissionStatus(Manifest.permission.POST_NOTIFICATIONS)
                val areNotificationsEnabled: Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()
                if (runtimeStatus == PermissionStatus.GRANTED && areNotificationsEnabled) {
                    PermissionStatus.GRANTED
                } else {
                    PermissionStatus.DENIED
                }
            } else {
                if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    PermissionStatus.GRANTED
                } else {
                    PermissionStatus.DENIED
                }
            }
        }.getOrElse { _ ->
            mindboxLogE("Unknown error checking notification permission status")
            PermissionStatus.NOT_DETERMINED
        }
    }

    override fun getPhotoLibraryPermissionStatus(): PermissionStatus {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val imagesStatus: PermissionStatus = resolveRuntimePermissionStatus(Manifest.permission.READ_MEDIA_IMAGES)
                if (imagesStatus == PermissionStatus.GRANTED) {
                    return@runCatching PermissionStatus.GRANTED
                }
                val selectedPhotosGranted: Boolean = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                ) == PackageManager.PERMISSION_GRANTED
                if (selectedPhotosGranted) {
                    PermissionStatus.LIMITED
                } else {
                    imagesStatus
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                resolveRuntimePermissionStatus(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                resolveRuntimePermissionStatus(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.getOrElse { _ ->
            mindboxLogE("Unknown error checking photo library permission status")
            PermissionStatus.NOT_DETERMINED
        }
    }

    private fun resolveRuntimePermissionStatus(permission: String): PermissionStatus {
        val isGranted: Boolean = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        return if (isGranted) PermissionStatus.GRANTED else PermissionStatus.DENIED
    }
}
