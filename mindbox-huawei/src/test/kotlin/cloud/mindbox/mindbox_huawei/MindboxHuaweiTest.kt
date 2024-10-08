package cloud.mindbox.mindbox_huawei

import com.huawei.hms.push.RemoteMessage
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test

class MindboxHuaweiTest {

    private val remoteMessageWithData = mockk<RemoteMessage>()
    private val remoteMessageWithoutData = mockk<RemoteMessage>()
    private val remoteMessageWithoutTitle = mockk<RemoteMessage>()

    init {
        every { remoteMessageWithData.data } returns mapOf(
            "uniqueKey" to "key",
            "title" to "title",
            "message" to "message",
            "imageUrl" to "image",
            "buttons" to "[{\"uniqueKey\": \"button_key\", \"text\": \"button_text\", \"url\": \"button_url\"}]",
            "payload" to "payload"
        ).toString()
        every { remoteMessageWithoutData.data } returns ""
        every { remoteMessageWithoutTitle.data } returns mapOf(
            "uniqueKey" to "key",
            "message" to "message",
            "imageUrl" to "image",
            "buttons" to "[{\"uniqueKey\": \"button_key\", \"text\": \"button_text\", \"url\": \"button_url\"}]",
            "clickUrl" to "clickUrl",
            "payload" to "payload"
        ).toString()
    }

    @Test
    fun `isMindboxPush returns true when map has uniqueKey`() {
        val result = MindboxHuawei.isMindboxPush(remoteMessageWithData)
        Assert.assertTrue(result)
    }

    @Test
    fun `isMindboxPush returns false when map doesn't have uniqueKey`() {
        val result = MindboxHuawei.isMindboxPush(remoteMessageWithoutData)
        Assert.assertFalse(result)
    }

    @Test
    fun `convertToMindboxRemoteMessage returns valid MindboxRemoteMessage`() {
        val result = MindboxHuawei.convertToMindboxRemoteMessage(remoteMessageWithData)
        Assert.assertNotNull(result)
        Assert.assertEquals("key", result?.uniqueKey)
    }

    @Test
    fun `convertToMindboxRemoteMessage returns null with wrong data`() {
        val result = MindboxHuawei.convertToMindboxRemoteMessage(remoteMessageWithoutData)
        Assert.assertNull(result)
    }

    @Test
    fun `convertToMindboxRemoteMessage returns non null for remote message without title`() {
        val mindboxRemoteMessage =
            MindboxHuawei.convertToMindboxRemoteMessage(remoteMessageWithoutTitle)
        Assert.assertNotNull(mindboxRemoteMessage)
        Assert.assertEquals("", mindboxRemoteMessage?.title)
    }
}
