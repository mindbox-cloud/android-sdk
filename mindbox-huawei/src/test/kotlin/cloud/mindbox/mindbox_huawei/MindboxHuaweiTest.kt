package cloud.mindbox.mindbox_huawei

import com.huawei.hms.push.RemoteMessage
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert
import org.junit.Assert.assertNotNull
import org.junit.Test

class MindboxHuaweiTest {

    private val remoteMessageWithData = mockk<RemoteMessage>()
    private val remoteMessageWithoutData = mockk<RemoteMessage>()
    private val remoteMessageWithoutTitle = mockk<RemoteMessage>()
    private val remoteMessageWithoutButtons = mockk<RemoteMessage>()

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
        every { remoteMessageWithoutButtons.data } returns mapOf(
            "uniqueKey" to "key",
            "title" to "title",
            "message" to "message",
            "imageUrl" to "image",
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

    @Test
    fun `convertToMindboxRemoteMessage returns pushActions as empty list when buttons is missing`() {
        val mindboxRemoteMessage =
            MindboxHuawei.convertToMindboxRemoteMessage(remoteMessageWithoutButtons)
        Assert.assertNotNull(mindboxRemoteMessage)
        Assert.assertEquals(0, mindboxRemoteMessage?.pushActions?.size)
    }

    @Test
    fun `convertToMindboxRemoteMessage returns pushActions when buttons is string`() {
        val jsonDataWithButtonsString = """
            {
              "uniqueKey": "key_str",
              "title": "title_str",
              "message": "message_str",
              "buttons": "[{\"uniqueKey\":\"btn1\",\"text\":\"Button1\",\"url\":\"https://example.com/1\"},{\"uniqueKey\":\"btn2\",\"text\":\"Button2\",\"url\":\"https://example.com/2\"}]",
              "clickUrl": "clickUrl_str",
              "imageUrl": "image_str",
              "payload": "payload_str"
            }
        """.trimIndent()
        val remoteMessage = mockk<RemoteMessage>()
        every { remoteMessage.data } returns jsonDataWithButtonsString

        val mindboxRemoteMessage = MindboxHuawei.convertToMindboxRemoteMessage(remoteMessage)
        assertNotNull(mindboxRemoteMessage)
        Assert.assertEquals(2, mindboxRemoteMessage?.pushActions?.size)
    }
}
