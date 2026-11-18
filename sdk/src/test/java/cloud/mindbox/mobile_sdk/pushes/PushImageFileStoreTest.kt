package cloud.mindbox.mobile_sdk.pushes

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.pushes.handler.image.PushImageFileStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PushImageFileStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Timestamp(1_800_000_000_000L)
    private val twoHoursAgo = Timestamp(now.ms - 7_200_000L)
    private val tenMinutesAgo = Timestamp(now.ms - 600_000L)

    @Test
    fun `save keeps each image in its own gif file under the push images directory`() {
        val bytes = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61)

        val first = PushImageFileStore.save(context, bytes.inputStream())
        val second = PushImageFileStore.save(context, bytes.inputStream())

        assertEquals(File(context.cacheDir, "mindbox_push_images"), first.parentFile)
        assertEquals("gif", first.extension)
        assertArrayEquals(bytes, first.readBytes())
        assertNotEquals(first, second)
    }

    @Test
    fun `uriFor points at the sdk provider, which serves the saved bytes`() {
        val bytes = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61)
        val file = PushImageFileStore.save(context, bytes.inputStream())

        val uri = PushImageFileStore.uriFor(context, file)

        assertEquals("content://${context.packageName}.mindbox.pushimages/push_images/${file.name}", uri.toString())
        val served = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        assertArrayEquals(bytes, served)
    }

    @Test
    fun `removeStale deletes only files older than an hour that no active notification shows`() {
        val oldUnused = savedFile(lastModified = twoHoursAgo)
        val recentUnused = savedFile(lastModified = tenMinutesAgo)
        val oldShown = savedFile(lastModified = twoHoursAgo)

        PushImageFileStore.removeStale(context, listOf(notificationShowing(oldShown)), now)

        assertEquals(false, oldUnused.exists())
        assertEquals(true, recentUnused.exists())
        assertEquals(true, oldShown.exists())
    }

    private fun savedFile(lastModified: Timestamp): File =
        PushImageFileStore.save(context, byteArrayOf(1).inputStream()).apply { setLastModified(lastModified.ms) }

    private fun notificationShowing(file: File): Notification =
        NotificationCompat.Builder(context, "channel")
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(
                        Icon.createWithContentUri(
                            "content://${context.packageName}.mindbox.pushimages/push_images/${file.name}",
                        ),
                    ),
            )
            .build()
}
