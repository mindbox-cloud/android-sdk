package cloud.mindbox.mobile_sdk.pushes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.pushes.handler.MessageHandlingState
import cloud.mindbox.mobile_sdk.pushes.handler.image.MindboxImageLoaderDefault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MindboxImageLoaderDefaultTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val loader = MindboxImageLoaderDefault()
    private val state = MessageHandlingState(attemptNumber = 1, isMessageDisplayed = false)
    private val pushImagesDirectory = File(context.cacheDir, "mindbox_push_images")

    @Test
    fun `a gif on android 14 keeps its first frame and a content uri to the saved file`() {
        val image = loader.loadPushImage(context, messageWithImage("two_frames.gif"), state, sdkInt = 34)

        assertNotNull(image?.bitmap)
        assertEquals("${context.packageName}.mindbox.pushimages", image?.animatedImageUri?.authority)
        assertEquals(1, savedFiles().size)
    }

    @Test
    fun `a gif below android 14 comes back as a still image and saves nothing`() {
        val image = loader.loadPushImage(context, messageWithImage("two_frames.gif"), state, sdkInt = 33)

        assertNotNull(image?.bitmap)
        assertNull(image?.animatedImageUri)
        assertEquals(0, savedFiles().size)
    }

    @Test
    fun `a png on android 14 comes back as a still image and saves nothing`() {
        val image = loader.loadPushImage(context, messageWithImage("four_pixels.png"), state, sdkInt = 34)

        assertNotNull(image?.bitmap)
        assertNull(image?.animatedImageUri)
        assertEquals(0, savedFiles().size)
    }

    @Test
    fun `a gif whose first frame does not decode gives no image and leaves no file`() {
        val image = loader.loadPushImage(context, messageWithImage("broken.gif"), state, sdkInt = 34)

        assertNull(image)
        assertEquals(0, savedFiles().size)
    }

    private fun savedFiles(): List<File> = pushImagesDirectory.listFiles()?.toList().orEmpty()

    private fun messageWithImage(fixture: String) = MindboxRemoteMessage(
        uniqueKey = "unique_key",
        title = "Title",
        description = "Description",
        pushActions = emptyList(),
        pushLink = null,
        imageUrl = checkNotNull(javaClass.classLoader?.getResource("push_images/$fixture")).toString(),
        payload = null,
    )
}
