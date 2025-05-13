package cloud.mindbox.mobile_sdk.pushes

import org.junit.Assert.*
import org.junit.Test

class MindboxPushConverterTest {

    private val converter = TestMindboxPushConverter()

    @Test
    fun `isMindboxPush returns false if data is empty`() {
        val data = mapOf<String, String>()

        assertFalse(converter.isMindboxPush(TestRemoteMessage(data)))
        assertFalse(converter.isMindboxPush(data))
    }

    @Test
    fun `isMindboxPush returns false if data doesn't contain unique key`() {
        val data = mapOf(
            "key1" to "value1",
            "key2" to "value2"
        )

        assertFalse(converter.isMindboxPush(TestRemoteMessage(data)))
        assertFalse(converter.isMindboxPush(data))
    }

    @Test
    fun `isMindboxPush returns true if data contains unique key`() {
        val data = mapOf(
            "uniqueKey" to "test-unique-key",
            "key1" to "value1"
        )

        assertTrue(converter.isMindboxPush(TestRemoteMessage(data)))
        assertTrue(converter.isMindboxPush(data))
    }

    @Test
    fun `isMindboxPush returns true if data contains all required fields`() {
        val data = mapOf(
            "uniqueKey" to "test-unique-key",
            "title" to "Test Title",
            "message" to "Test Message",
            "buttons" to """[
                {
                    "uniqueKey": "00000000-0000-0000-0000-000000000000",
                    "text": "TEXT",
                    "url": "https://www.google.com"
                }
            ]""",
            "clickUrl" to "https://test.com",
            "imageUrl" to "https://test.com/image.jpg",
            "payload" to "test-payload"
        )

        assertTrue(converter.isMindboxPush(TestRemoteMessage(data)))
        assertTrue(converter.isMindboxPush(data))
    }

    @Test
    fun `isMindboxPush returns true with valid buttons JSON`() {
        val data = mapOf(
            "uniqueKey" to "test-unique-key",
            "buttons" to """[
                {
                    "uniqueKey": "00000000-0000-0000-0000-000000000000",
                    "text": "TEXT",
                    "url": "https://www.google.com"
                }
            ]"""
        )

        assertTrue(converter.isMindboxPush(TestRemoteMessage(data)))
        assertTrue(converter.isMindboxPush(data))
    }

    @Test
    fun `isMindboxPush returns false if unique key is empty`() {
        val data = mapOf(
            "uniqueKey" to "",
            "key1" to "value1"
        )

        assertFalse(converter.isMindboxPush(TestRemoteMessage(data)))
        assertFalse(converter.isMindboxPush(data))
    }

    @Test
    fun `convertToMindboxRemoteMessage returns null for null input`() {
        assertNull(converter.convertToMindboxRemoteMessage(null as TestRemoteMessage?))
    }

    @Test
    fun `convertToMindboxRemoteMessage returns null for empty data`() {
        val data = mapOf<String, String>()
        assertNull(converter.convertToMindboxRemoteMessage(TestRemoteMessage(data)))
        assertNull(converter.convertToMindboxRemoteMessage(data))
    }

    @Test
    fun `convertToMindboxRemoteMessage returns null for data without unique key`() {
        val data = mapOf(
            "key1" to "value1",
            "key2" to "value2"
        )
        assertNull(converter.convertToMindboxRemoteMessage(TestRemoteMessage(data)))
        assertNull(converter.convertToMindboxRemoteMessage(data))
    }

    @Test
    fun `convertToMindboxRemoteMessage returns null for empty unique key`() {
        val data = mapOf(
            "uniqueKey" to "",
            "key1" to "value1"
        )
        assertNull(converter.convertToMindboxRemoteMessage(TestRemoteMessage(data)))
        assertNull(converter.convertToMindboxRemoteMessage(data))
    }

    @Test
    fun `convertToMindboxRemoteMessage returns valid object with minimal data`() {
        val data = mapOf(
            "uniqueKey" to "test-unique-key"
        )

        val result = converter.convertToMindboxRemoteMessage(data)
        assertNotNull(result)
        assertEquals("test-unique-key", result?.uniqueKey)
        assertEquals("", result?.title)
        assertEquals("", result?.description)
        assertTrue(result?.pushActions?.isEmpty() ?: false)
        assertNull(result?.pushLink)
        assertNull(result?.imageUrl)
        assertNull(result?.payload)
    }

    @Test
    fun `convertToMindboxRemoteMessage returns valid object with full data`() {
        val data = mapOf(
            "uniqueKey" to "test-unique-key",
            "title" to "Test Title",
            "message" to "Test Message",
            "buttons" to """[
                {
                    "uniqueKey": "00000000-0000-0000-0000-000000000000",
                    "text": "TEXT",
                    "url": "https://www.google.com"
                }
            ]""",
            "clickUrl" to "https://test.com",
            "imageUrl" to "https://test.com/image.jpg",
            "payload" to "test-payload"
        )

        val result = converter.convertToMindboxRemoteMessage(data)
        assertNotNull(result)
        assertEquals("test-unique-key", result?.uniqueKey)
        assertEquals("Test Title", result?.title)
        assertEquals("Test Message", result?.description)
        assertEquals(1, result?.pushActions?.size)
        assertEquals("https://test.com", result?.pushLink)
        assertEquals("https://test.com/image.jpg", result?.imageUrl)
        assertEquals("test-payload", result?.payload)

        val button = result?.pushActions?.first()
        assertNotNull(button)
        assertEquals("00000000-0000-0000-0000-000000000000", button?.uniqueKey)
        assertEquals("TEXT", button?.text)
        assertEquals("https://www.google.com", button?.url)
    }

    @Test
    fun `convertToMindboxRemoteMessage returns same object with full data`() {
        val data = mapOf(
            "uniqueKey" to "test-unique-key",
            "title" to "Test Title",
            "message" to "Test Message",
            "buttons" to """[
                {
                    "uniqueKey": "00000000-0000-0000-0000-000000000000",
                    "text": "TEXT",
                    "url": "https://www.google.com"
                }
            ]""",
            "clickUrl" to "https://test.com",
            "imageUrl" to "https://test.com/image.jpg",
            "payload" to "test-payload"
        )

        val result1 = converter.convertToMindboxRemoteMessage(data)
        val result2 = converter.convertToMindboxRemoteMessage(TestRemoteMessage(data))
        assertEquals(result1, result2)
        assertNotNull(result1)
    }

    @Test
    fun `convertToMindboxRemoteMessage handles invalid buttons JSON`() {
        val data = mapOf(
            "uniqueKey" to "test-unique-key",
            "buttons" to "invalid json"
        )

        val result = converter.convertToMindboxRemoteMessage(data)
        assertNotNull(result)
        assertEquals("test-unique-key", result?.uniqueKey)
        assertTrue(result?.pushActions?.isEmpty() ?: false)
    }

    @Test
    fun `convertToMindboxRemoteMessage returns valid object with empty buttons array`() {
        val data = mapOf(
            "uniqueKey" to "test-unique-key",
            "buttons" to "[]"
        )

        val result = converter.convertToMindboxRemoteMessage(data)
        assertNotNull(result)
        assertEquals("test-unique-key", result?.uniqueKey)
        assertTrue(result?.pushActions?.isEmpty() ?: false)
    }

    @Test
    fun `convertToMindboxRemoteMessage returns valid object with single button`() {
        val data = mapOf(
            "uniqueKey" to "test-unique-key",
            "buttons" to """[
                {
                    "uniqueKey": "00000000-0000-0000-0000-000000000000",
                    "text": "First Button",
                    "url": "https://www.google.com"
                }
            ]"""
        )

        val result = converter.convertToMindboxRemoteMessage(data)
        assertNotNull(result)
        assertEquals("test-unique-key", result?.uniqueKey)
        assertEquals(1, result?.pushActions?.size)

        val button = result?.pushActions?.first()
        assertNotNull(button)
        assertEquals("00000000-0000-0000-0000-000000000000", button?.uniqueKey)
        assertEquals("First Button", button?.text)
        assertEquals("https://www.google.com", button?.url)
    }

    @Test
    fun `convertToMindboxRemoteMessage returns valid object with multiple buttons`() {
        val data = mapOf(
            "uniqueKey" to "test-unique-key",
            "buttons" to """[
                {
                    "uniqueKey": "00000000-0000-0000-0000-000000000000",
                    "text": "First Button",
                    "url": "https://www.google.com"
                },
                {
                    "uniqueKey": "11111111-1111-1111-1111-111111111111",
                    "text": "Second Button",
                    "url": "https://www.mindbox.ru"
                }
            ]"""
        )

        val result = converter.convertToMindboxRemoteMessage(data)
        assertNotNull(result)
        assertEquals("test-unique-key", result?.uniqueKey)
        assertEquals(2, result?.pushActions?.size)

        val firstButton = result?.pushActions?.first()
        assertNotNull(firstButton)
        assertEquals("00000000-0000-0000-0000-000000000000", firstButton?.uniqueKey)
        assertEquals("First Button", firstButton?.text)
        assertEquals("https://www.google.com", firstButton?.url)

        val secondButton = result?.pushActions?.last()
        assertNotNull(secondButton)
        assertEquals("11111111-1111-1111-1111-111111111111", secondButton?.uniqueKey)
        assertEquals("Second Button", secondButton?.text)
        assertEquals("https://www.mindbox.ru", secondButton?.url)
    }

    @Test
    fun `convertToMindboxRemoteMessage handles buttons with missing optional fields`() {
        val data = mapOf(
            "uniqueKey" to "test-unique-key",
            "buttons" to """[
                {
                    "uniqueKey": "00000000-0000-0000-0000-000000000000",
                    "text": "Button without URL"
                }
            ]"""
        )

        val result = converter.convertToMindboxRemoteMessage(data)
        assertNotNull(result)
        assertEquals("test-unique-key", result?.uniqueKey)
        assertEquals(1, result?.pushActions?.size)

        val button = result?.pushActions?.first()
        assertNotNull(button)
        assertEquals("00000000-0000-0000-0000-000000000000", button?.uniqueKey)
        assertEquals("Button without URL", button?.text)
        assertNull(button?.url)
    }

    @Test
    fun `convertToMindboxRemoteMessage handles invalid button data`() {
        val data = mapOf(
            "uniqueKey" to "test-unique-key",
            "buttons" to """[
                {
                    "text": "Button without uniqueKey"
                }
            ]"""
        )

        val result = converter.convertToMindboxRemoteMessage(data)
        assertNotNull(result)
        assertEquals("test-unique-key", result?.uniqueKey)
        assertTrue(result!!.pushActions.size == 1)
    }

    data class TestRemoteMessage(val data: Map<String, String> = mapOf())

    class TestMindboxPushConverter : MindboxPushConverter<TestRemoteMessage>() {
        override fun TestRemoteMessage.pushData(): Map<String, String> = data
    }
}
