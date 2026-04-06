package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.app.Activity
import android.os.Build
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionStatus
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WebViewPermissionRequesterTest {

    @Test
    fun `requestPermission returns granted when push permission already granted`() = runTest {
        val pushPermissionLauncher: PushPermissionLauncher = FakePushPermissionLauncher(
            PushPermissionRequestResult(
                status = PermissionRequestStatus.DENIED,
                shouldShowRequestPermissionRationale = false,
                dialogShown = false
            )
        )
        val permissionManager: PermissionManager = FakePermissionManager(
            pushStatus = PermissionStatus.GRANTED
        )
        val requester: WebViewPermissionRequester = WebViewPermissionRequesterImpl(
            context = mockk(relaxed = true),
            pushPermissionLauncher = pushPermissionLauncher,
            permissionManager = permissionManager,
            sdkIntProvider = { Build.VERSION_CODES.TIRAMISU }
        )
        val actualResult: PermissionActionResponse = requester.requestPermission(
            activity = mockk(relaxed = true),
            permissionType = PermissionType.PUSH_NOTIFICATIONS
        )
        assertEquals(PermissionRequestStatus.GRANTED, actualResult.result)
        assertEquals(false, actualResult.dialogShown)
        assertEquals(true, actualResult.details.required)
        assertEquals(null, actualResult.details.shouldShowRequestPermissionRationale)
    }

    @Test
    fun `requestPermission returns granted when push permission request is granted without dialog`() = runTest {
        val pushPermissionLauncher: PushPermissionLauncher = FakePushPermissionLauncher(
            PushPermissionRequestResult(
                status = PermissionRequestStatus.GRANTED,
                shouldShowRequestPermissionRationale = false,
                dialogShown = false
            )
        )
        val permissionManager: PermissionManager = FakePermissionManager(
            pushStatus = PermissionStatus.DENIED
        )
        val requester: WebViewPermissionRequester = WebViewPermissionRequesterImpl(
            context = mockk(relaxed = true),
            pushPermissionLauncher = pushPermissionLauncher,
            permissionManager = permissionManager,
            sdkIntProvider = { Build.VERSION_CODES.TIRAMISU }
        )
        val actualResult: PermissionActionResponse = requester.requestPermission(
            activity = mockk(relaxed = true),
            permissionType = PermissionType.PUSH_NOTIFICATIONS
        )
        assertEquals(PermissionRequestStatus.GRANTED, actualResult.result)
        assertEquals(false, actualResult.dialogShown)
        assertEquals(true, actualResult.details.required)
        assertEquals(false, actualResult.details.shouldShowRequestPermissionRationale)
    }

    @Test
    fun `requestPermission returns denied when push permission request is denied`() = runTest {
        val pushPermissionLauncher: PushPermissionLauncher = FakePushPermissionLauncher(
            PushPermissionRequestResult(
                status = PermissionRequestStatus.DENIED,
                shouldShowRequestPermissionRationale = true,
                dialogShown = true
            )
        )
        val permissionManager: PermissionManager = FakePermissionManager(
            pushStatus = PermissionStatus.DENIED
        )
        val requester: WebViewPermissionRequester = WebViewPermissionRequesterImpl(
            context = mockk(relaxed = true),
            pushPermissionLauncher = pushPermissionLauncher,
            permissionManager = permissionManager,
            sdkIntProvider = { Build.VERSION_CODES.TIRAMISU }
        )
        val actualResult: PermissionActionResponse = requester.requestPermission(
            activity = mockk(relaxed = true),
            permissionType = PermissionType.PUSH_NOTIFICATIONS
        )
        assertEquals(PermissionRequestStatus.DENIED, actualResult.result)
        assertEquals(true, actualResult.dialogShown)
        assertEquals(true, actualResult.details.required)
        assertEquals(true, actualResult.details.shouldShowRequestPermissionRationale)
    }

    @Test
    fun `requestPermission returns denied without dialog for push on sdk lower than tiramisu`() = runTest {
        val pushPermissionLauncher: PushPermissionLauncher = FakePushPermissionLauncher(
            PushPermissionRequestResult(
                status = PermissionRequestStatus.GRANTED,
                shouldShowRequestPermissionRationale = false,
                dialogShown = false
            )
        )
        val permissionManager: PermissionManager = FakePermissionManager(
            pushStatus = PermissionStatus.DENIED
        )
        val requester: WebViewPermissionRequester = WebViewPermissionRequesterImpl(
            context = mockk(relaxed = true),
            pushPermissionLauncher = pushPermissionLauncher,
            permissionManager = permissionManager,
            sdkIntProvider = { Build.VERSION_CODES.S }
        )
        val actualResult: PermissionActionResponse = requester.requestPermission(
            activity = mockk(relaxed = true),
            permissionType = PermissionType.PUSH_NOTIFICATIONS
        )
        assertEquals(PermissionRequestStatus.DENIED, actualResult.result)
        assertEquals(false, actualResult.dialogShown)
        assertEquals(false, actualResult.details.required)
        assertEquals(null, actualResult.details.shouldShowRequestPermissionRationale)
    }

    private class FakePushPermissionLauncher(
        private val result: PushPermissionRequestResult
    ) : PushPermissionLauncher {
        override suspend fun requestPermission(activity: Activity): PushPermissionRequestResult {
            return result
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
