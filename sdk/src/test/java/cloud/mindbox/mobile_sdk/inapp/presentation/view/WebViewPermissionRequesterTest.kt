package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.app.Activity
import android.os.Build
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionStatus
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewPermissionRequesterTest {

    @Test
    fun `requestPermission returns granted when camera permission already granted`() = runTest {
        val runtimePermissionLauncher: RuntimePermissionLauncher = FakeRuntimePermissionLauncher(PermissionRequestStatus.DENIED)
        val permissionManifestChecker: PermissionManifestChecker = FakePermissionManifestChecker(
            declaredPermissions = setOf(android.Manifest.permission.CAMERA)
        )
        val permissionManager: PermissionManager = FakePermissionManager(
            cameraStatus = PermissionStatus.GRANTED
        )
        val requester: WebViewPermissionRequester = WebViewPermissionRequesterImpl(
            context = mockk(relaxed = true),
            runtimePermissionLauncher = runtimePermissionLauncher,
            manifestPermissionChecker = permissionManifestChecker,
            permissionManager = permissionManager,
            sdkIntProvider = { Build.VERSION_CODES.TIRAMISU }
        )
        val actualResult: PermissionActionResponse = requester.requestPermission(
            activity = mockk(relaxed = true),
            permissionType = PermissionType.CAMERA
        )
        assertEquals(PermissionRequestStatus.GRANTED, actualResult.result)
        assertEquals(false, actualResult.dialogShown)
    }

    @Test
    fun `requestPermission returns denied when camera permission request is denied`() = runTest {
        val runtimePermissionLauncher: RuntimePermissionLauncher = FakeRuntimePermissionLauncher(PermissionRequestStatus.DENIED)
        val permissionManifestChecker: PermissionManifestChecker = FakePermissionManifestChecker(
            declaredPermissions = setOf(android.Manifest.permission.CAMERA)
        )
        val permissionManager: PermissionManager = FakePermissionManager(
            cameraStatus = PermissionStatus.DENIED
        )
        val requester: WebViewPermissionRequester = WebViewPermissionRequesterImpl(
            context = mockk(relaxed = true),
            runtimePermissionLauncher = runtimePermissionLauncher,
            manifestPermissionChecker = permissionManifestChecker,
            permissionManager = permissionManager,
            sdkIntProvider = { Build.VERSION_CODES.TIRAMISU }
        )
        val actualResult: PermissionActionResponse = requester.requestPermission(
            activity = mockk(relaxed = true),
            permissionType = PermissionType.CAMERA
        )
        assertEquals(PermissionRequestStatus.DENIED, actualResult.result)
        assertEquals(true, actualResult.dialogShown)
    }

    @Test
    fun `requestPermission throws error when permission missing in AndroidManifest`() = runTest {
        val runtimePermissionLauncher: RuntimePermissionLauncher = FakeRuntimePermissionLauncher(PermissionRequestStatus.GRANTED)
        val permissionManifestChecker: PermissionManifestChecker = FakePermissionManifestChecker(
            declaredPermissions = emptySet()
        )
        val permissionManager: PermissionManager = FakePermissionManager(
            cameraStatus = PermissionStatus.DENIED
        )
        val requester: WebViewPermissionRequester = WebViewPermissionRequesterImpl(
            context = mockk(relaxed = true),
            runtimePermissionLauncher = runtimePermissionLauncher,
            manifestPermissionChecker = permissionManifestChecker,
            permissionManager = permissionManager,
            sdkIntProvider = { Build.VERSION_CODES.TIRAMISU }
        )
        val error: Throwable = runCatching {
            requester.requestPermission(
                activity = mockk(relaxed = true),
                permissionType = PermissionType.CAMERA
            )
        }.exceptionOrNull() ?: error("Expected exception for missing manifest permission")
        assertTrue(error is IllegalStateException)
    }

    @Test
    fun `requestPermission returns denied without dialog for push on sdk lower than tiramisu`() = runTest {
        val runtimePermissionLauncher: RuntimePermissionLauncher = FakeRuntimePermissionLauncher(PermissionRequestStatus.GRANTED)
        val permissionManifestChecker: PermissionManifestChecker = FakePermissionManifestChecker(
            declaredPermissions = setOf(android.Manifest.permission.POST_NOTIFICATIONS)
        )
        val permissionManager: PermissionManager = FakePermissionManager(
            pushStatus = PermissionStatus.DENIED
        )
        val requester: WebViewPermissionRequester = WebViewPermissionRequesterImpl(
            context = mockk(relaxed = true),
            runtimePermissionLauncher = runtimePermissionLauncher,
            manifestPermissionChecker = permissionManifestChecker,
            permissionManager = permissionManager,
            sdkIntProvider = { Build.VERSION_CODES.S }
        )
        val actualResult: PermissionActionResponse = requester.requestPermission(
            activity = mockk(relaxed = true),
            permissionType = PermissionType.entries.first { permissionType: PermissionType ->
                permissionType.value == "pushNotifications"
            }
        )
        assertEquals(PermissionRequestStatus.DENIED, actualResult.result)
        assertEquals(false, actualResult.dialogShown)
    }

    @Test
    fun `requestPermission returns granted without dialog when status is limited`() = runTest {
        val runtimePermissionLauncher: RuntimePermissionLauncher = FakeRuntimePermissionLauncher(PermissionRequestStatus.DENIED)
        val permissionManifestChecker: PermissionManifestChecker = FakePermissionManifestChecker(
            declaredPermissions = setOf(android.Manifest.permission.READ_MEDIA_IMAGES)
        )
        val permissionManager: PermissionManager = FakePermissionManager(
            libraryStatus = PermissionStatus.LIMITED
        )
        val requester: WebViewPermissionRequester = WebViewPermissionRequesterImpl(
            context = mockk(relaxed = true),
            runtimePermissionLauncher = runtimePermissionLauncher,
            manifestPermissionChecker = permissionManifestChecker,
            permissionManager = permissionManager,
            sdkIntProvider = { Build.VERSION_CODES.UPSIDE_DOWN_CAKE }
        )
        val actualResult: PermissionActionResponse = requester.requestPermission(
            activity = mockk(relaxed = true),
            permissionType = PermissionType.entries.first { permissionType: PermissionType ->
                permissionType.value == "photoLibrary"
            }
        )
        assertEquals(PermissionRequestStatus.GRANTED, actualResult.result)
        assertEquals(false, actualResult.dialogShown)
    }

    private class FakeRuntimePermissionLauncher(
        private val result: PermissionRequestStatus
    ) : RuntimePermissionLauncher {
        override suspend fun requestPermission(activity: Activity, permissions: Array<String>): PermissionRequestStatus {
            return result
        }
    }

    private class FakePermissionManifestChecker(
        private val declaredPermissions: Set<String>
    ) : PermissionManifestChecker {
        override fun isPermissionDeclared(permission: String): Boolean {
            return declaredPermissions.contains(permission)
        }
    }

    private class FakePermissionManager(
        private val cameraStatus: PermissionStatus = PermissionStatus.DENIED,
        private val geoStatus: PermissionStatus = PermissionStatus.DENIED,
        private val microphoneStatus: PermissionStatus = PermissionStatus.DENIED,
        private val pushStatus: PermissionStatus = PermissionStatus.DENIED,
        private val libraryStatus: PermissionStatus = PermissionStatus.DENIED,
    ) : PermissionManager {

        override fun getCameraPermissionStatus(): PermissionStatus = cameraStatus

        override fun getLocationPermissionStatus(): PermissionStatus = geoStatus

        override fun getMicrophonePermissionStatus(): PermissionStatus = microphoneStatus

        override fun getNotificationPermissionStatus(): PermissionStatus = pushStatus

        override fun getPhotoLibraryPermissionStatus(): PermissionStatus = libraryStatus
    }
}
