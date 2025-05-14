package cloud.mindbox.mindbox_huawei

import com.huawei.hms.push.RemoteMessage
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class MindboxHuaweiTest {

    private val remoteMessageWithData = mockk<RemoteMessage>()
    private val remoteMessageWithoutData = mockk<RemoteMessage>()
    private val remoteMessageWithoutTitle = mockk<RemoteMessage>()
    private val remoteMessageWithoutButtons = mockk<RemoteMessage>()

    init {
        every { remoteMessageWithData.dataOfMap } returns mapOf(
            "uniqueKey" to "key",
            "title" to "title",
            "message" to "message",
            "imageUrl" to "image",
            "buttons" to "[{\"uniqueKey\": \"button_key\", \"text\": \"button_text\", \"url\": \"button_url\"}]",
            "payload" to "payload"
        )
        every { remoteMessageWithoutData.dataOfMap } returns mapOf()
        every { remoteMessageWithoutTitle.dataOfMap } returns mapOf(
            "uniqueKey" to "key",
            "message" to "message",
            "imageUrl" to "image",
            "buttons" to "[{\"uniqueKey\": \"button_key\", \"text\": \"button_text\", \"url\": \"button_url\"}]",
            "clickUrl" to "clickUrl",
            "payload" to "payload"
        )
        every { remoteMessageWithoutButtons.dataOfMap } returns mapOf(
            "uniqueKey" to "key",
            "title" to "title",
            "message" to "message",
            "imageUrl" to "image",
            "payload" to "payload"
        )
    }

    @Test
    fun `isMindboxPush returns true when map has uniqueKey`() {
        val result = MindboxHuawei.isMindboxPush(remoteMessageWithData)
        assertTrue(result)
    }

    @Test
    fun `isMindboxPush returns false when map doesn't have uniqueKey`() {
        val result = MindboxHuawei.isMindboxPush(remoteMessageWithoutData)
        assertFalse(result)
    }

    @Test
    fun `convertToMindboxRemoteMessage returns valid MindboxRemoteMessage`() {
        val result = MindboxHuawei.convertToMindboxRemoteMessage(remoteMessageWithData)
        assertNotNull(result)
        assertEquals("key", result?.uniqueKey)
    }

    @Test
    fun `convertToMindboxRemoteMessage returns null with wrong data`() {
        val result = MindboxHuawei.convertToMindboxRemoteMessage(remoteMessageWithoutData)
        assertNull(result)
    }

    @Test
    fun `convertToMindboxRemoteMessage returns non null for remote message without title`() {
        val mindboxRemoteMessage =
            MindboxHuawei.convertToMindboxRemoteMessage(remoteMessageWithoutTitle)
        assertNotNull(mindboxRemoteMessage)
        assertEquals("", mindboxRemoteMessage?.title)
    }

    @Test
    fun `convertToMindboxRemoteMessage returns pushActions as empty list when buttons is missing`() {
        val mindboxRemoteMessage =
            MindboxHuawei.convertToMindboxRemoteMessage(remoteMessageWithoutButtons)
        assertNotNull(mindboxRemoteMessage)
        assertEquals(0, mindboxRemoteMessage?.pushActions?.size)
    }

    @Test
    fun `convertToMindboxRemoteMessage returns pushActions when buttons is string`() {
        val jsonDataWithButtonsString = mapOf(
            "uniqueKey" to "key_str",
            "title" to "title_str",
            "message" to "message_str",
            "buttons" to "[{\"uniqueKey\":\"btn1\",\"text\":\"Button1\",\"url\":\"https://example.com/1\"},{\"uniqueKey\":\"btn2\",\"text\":\"Button2\",\"url\":\"https://example.com/2\"}]",
            "clickUrl" to "clickUrl_str",
            "imageUrl" to "image_str",
            "payload" to "payload_str"
        )
        val remoteMessage = mockk<RemoteMessage>()
        every { remoteMessage.dataOfMap } returns jsonDataWithButtonsString

        val mindboxRemoteMessage = MindboxHuawei.convertToMindboxRemoteMessage(remoteMessage)
        assertNotNull(mindboxRemoteMessage)
        assertEquals(2, mindboxRemoteMessage?.pushActions?.size)
    }
}
