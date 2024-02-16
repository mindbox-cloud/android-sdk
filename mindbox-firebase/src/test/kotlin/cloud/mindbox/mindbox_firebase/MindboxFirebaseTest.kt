package cloud.mindbox.mindbox_firebase

import com.google.firebase.messaging.RemoteMessage
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test

class MindboxFirebaseTest {


    private val remoteMessageWithData = mockk<RemoteMessage>()
    private val remoteMessageWithoutData = mockk<RemoteMessage>()

    init {
        every { remoteMessageWithData.data } returns mapOf(
            "uniqueKey" to "any-value1",
            "title" to "any-value2",
            "message" to "any-value3",
            "imageUrl" to "any-value4",
            "buttons" to "any-value5",
            "clickUrl" to "any-value6",
            "payload" to "any-value7"
        )
        every { remoteMessageWithoutData.data } returns emptyMap()
    }

    @Test
    fun `isMindboxPush returns true when remote message contains mindbox data`() {
        val isMindboxPush = MindboxFirebase.isMindboxPush(remoteMessageWithData)
        Assert.assertTrue(isMindboxPush)
    }

    @Test
    fun `isMindboxPush returns false when remote message does not contain mindbox data`() {
        val isMindboxPush = MindboxFirebase.isMindboxPush(remoteMessageWithoutData)
        Assert.assertFalse(isMindboxPush)
    }

    @Test
    fun `convertToMindboxRemoteMessage returns non-null MindboxRemoteMessage for valid data`() {
        val mindboxRemoteMessage =
            MindboxFirebase.convertToMindboxRemoteMessage(remoteMessageWithData)
        Assert.assertNotNull(mindboxRemoteMessage)
        Assert.assertEquals("any-value1", mindboxRemoteMessage?.uniqueKey)
    }

    @Test
    fun `convertToMindboxRemoteMessage returns null for invalid or no data`() {
        val mindboxRemoteMessage =
            MindboxFirebase.convertToMindboxRemoteMessage(remoteMessageWithoutData)
        Assert.assertNull(mindboxRemoteMessage)
    }
}

