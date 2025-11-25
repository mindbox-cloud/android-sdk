package cloud.mindbox.mobile_sdk.pushes

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class MindboxRemoteMessageTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val activityClass = TestActivity::class.java

    @Test
    fun `getPushContentIntent creates correct PendingIntent`() {
        val uniqueKey = "unique_key"
        val payload = "payload_value"
        val url = "http://example.com"
        val message = MindboxRemoteMessage(
            uniqueKey = uniqueKey,
            title = "Title",
            description = "Description",
            pushActions = emptyList(),
            pushLink = url,
            imageUrl = null,
            payload = payload
        )
        val notificationId = 123
        val extras = Bundle().apply { putString("key", "value") }

        val pendingIntent = message.getPushContentIntent(
            context = context,
            activity = activityClass,
            notificationId = notificationId,
            extras = extras
        )

        assertNotNull(pendingIntent)
        val intent = Shadows.shadowOf(pendingIntent).savedIntent
        assertNotNull(intent)
        
        assertEquals(activityClass.name, intent.component?.className)
        assertEquals(payload, intent.getStringExtra("push_payload"))
        assertEquals(notificationId, intent.getIntExtra("notification_id", -1))
        assertEquals(uniqueKey, intent.getStringExtra(PushNotificationManager.EXTRA_UNIQ_PUSH_KEY))
        assertEquals(null, intent.getStringExtra(PushNotificationManager.EXTRA_UNIQ_PUSH_BUTTON_KEY))
        assertTrue(intent.getBooleanExtra(PushNotificationManager.IS_OPENED_FROM_PUSH_BUNDLE_KEY, false))
        assertEquals(url, intent.getStringExtra("push_url"))
        assertEquals("value", intent.extras?.getString("key"))
    }

    @Test
    fun `getPushActionIntent creates correct PendingIntent`() {
        val uniqueKey = "unique_key"
        val payload = "payload_value"
        val message = MindboxRemoteMessage(
            uniqueKey = uniqueKey,
            title = "Title",
            description = "Description",
            pushActions = emptyList(),
            pushLink = null,
            imageUrl = null,
            payload = payload
        )
        val actionUrl = "http://action.com"
        val actionKey = "action_key"
        val pushAction = PushAction(
            uniqueKey = actionKey,
            text = "Action",
            url = actionUrl
        )
        val notificationId = 123

        val pendingIntent = message.getPushActionIntent(
            context = context,
            activity = activityClass,
            notificationId = notificationId,
            pushAction = pushAction
        )

        assertNotNull(pendingIntent)
        val intent = Shadows.shadowOf(pendingIntent).savedIntent
        assertNotNull(intent)
        
        assertEquals(activityClass.name, intent.component?.className)
        assertEquals(payload, intent.getStringExtra("push_payload"))
        assertEquals(notificationId, intent.getIntExtra("notification_id", -1))
        assertEquals(uniqueKey, intent.getStringExtra(PushNotificationManager.EXTRA_UNIQ_PUSH_KEY))
        assertEquals(actionKey, intent.getStringExtra(PushNotificationManager.EXTRA_UNIQ_PUSH_BUTTON_KEY))
        assertTrue(intent.getBooleanExtra(PushNotificationManager.IS_OPENED_FROM_PUSH_BUNDLE_KEY, false))
        assertEquals(actionUrl, intent.getStringExtra("push_url"))
    }
}

class TestActivity : Activity()

