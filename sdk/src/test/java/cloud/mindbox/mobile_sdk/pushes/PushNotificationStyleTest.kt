package cloud.mindbox.mobile_sdk.pushes

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.os.BundleCompat
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.pushes.handler.image.PushImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PushNotificationStyleTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val firstFrame = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    @Test
    fun `an animated image reaches the big picture as a content uri and keeps its first frame as the large icon`() {
        val uri = Uri.parse("content://${context.packageName}.mindbox.pushimages/push_images/image.gif")

        val notification = build(PushImage(bitmap = firstFrame, animatedImageUri = uri))

        val picture = BundleCompat.getParcelable(notification.extras, Notification.EXTRA_PICTURE_ICON, Icon::class.java)
        assertEquals(Icon.TYPE_URI, picture?.type)
        assertEquals(uri, picture?.uri)
        assertNotNull(notification.getLargeIcon())
    }

    @Test
    fun `a still image keeps the bitmap big picture`() {
        val notification = build(PushImage(bitmap = firstFrame, animatedImageUri = null))

        assertNotNull(BundleCompat.getParcelable(notification.extras, Notification.EXTRA_PICTURE, Bitmap::class.java))
        assertNull(BundleCompat.getParcelable(notification.extras, Notification.EXTRA_PICTURE_ICON, Icon::class.java))
    }

    private fun build(image: PushImage): Notification = with(PushNotificationManager) {
        NotificationCompat.Builder(context, "channel")
            .setNotificationStyle(image = image, title = "Title", text = "Text", hasButtons = false, context = context)
            .build()
    }
}
