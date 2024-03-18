package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MindboxNotificationManagerImplTest {

    private lateinit var context: Context
    private lateinit var activity: Activity

    @MockK
    private lateinit var mindboxNotificationManager: MindboxNotificationManager

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        activity = mockk(relaxed = true)
        mindboxNotificationManager = MindboxNotificationManagerImpl(context)
    }

    @Test
    fun `isNotificationEnabled returns true when notifications are enabled`() {
        every { NotificationManagerCompat.from(context).areNotificationsEnabled() } returns true
        assertTrue(mindboxNotificationManager.isNotificationEnabled())
    }

    @Test
    fun `isNotificationEnabled return false when notification aren't enabled`() {
        every { NotificationManagerCompat.from(context).areNotificationsEnabled() } returns false
        assertFalse(mindboxNotificationManager.isNotificationEnabled())
    }
    @Test
    fun `isNotificationEnabled handles exception and returns true by default`() {
        every { NotificationManagerCompat.from(context).areNotificationsEnabled() } throws RuntimeException("Test exception")
        assertTrue(mindboxNotificationManager.isNotificationEnabled())
    }

    @Test
    fun `openNotificationSettings opens notification settings for the given activity`() {
        mindboxNotificationManager.openNotificationSettings(activity)
        verify(exactly = 1) { activity.startActivity(any()) }
    }

    @Test
    fun `requestPermission does nothing if notifications are enabled`() {
        every { NotificationManagerCompat.from(context).areNotificationsEnabled() } returns true

        mindboxNotificationManager.requestPermission(activity)

        verify(exactly = 0) { activity.requestPermissions(any(),any()) }
        verify(exactly = 0) { activity.startActivity(any()) }
    }

    @Test
    fun `requestPermission open temp activity for request permission`() {
        every { NotificationManagerCompat.from(context).areNotificationsEnabled() } returns false
        every { activity.shouldShowRequestPermissionRationale(any()) } returns false

        mindboxNotificationManager.requestPermission(activity)

        verify(exactly = 1) { activity.startActivity(any()) }
    }

    @Test
    fun `requestPermission requests permission if notifications are disabled and first request was rejected`() {
        every { NotificationManagerCompat.from(context).areNotificationsEnabled() } returns false
        every { activity.shouldShowRequestPermissionRationale(any()) } returns true

        mindboxNotificationManager.requestPermission(activity)

        verify(exactly = 1) { activity.requestPermissions(any(), any()) }
    }
}