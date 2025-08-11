package cloud.mindbox.mobile_sdk

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.android.volley.NetworkResponse
import com.android.volley.VolleyError
import com.jakewharton.threetenabp.AndroidThreeTen
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId
import org.threeten.bp.ZoneOffset
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.util.LinkedList
import java.util.Queue

@RunWith(RobolectricTestRunner::class)
internal class ExtensionsTest {

    @Before
    fun onTestStart() {
        AndroidThreeTen.init(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `converting zoned date time to string`() {
        val time: ZonedDateTime = ZonedDateTime.now()
        val expectedResult = time.withZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))
        val actualResult = time.convertToString()
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `converting string to zoned date time`() {
        val time = "2023-01-27T14:13:29"
        val expectedResult: ZonedDateTime =
            LocalDateTime.parse(time, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")).atZone(
                ZoneOffset.UTC
            )
        val actualResult = time.convertToZonedDateTime()
        assertEquals(expectedResult, actualResult)
    }

    private val testPackageName = "com.test.app"
    private val customProcessName = "com.test.app:myprocess"
    private val context = mockk<Context> {
        every { packageName } returns testPackageName
    }

    @Test
    fun `isMainProcess if process resource empty`() {
        every { context.getString(any()) } returns ""
        assertTrue(context.isMainProcess(testPackageName))
    }

    @Test
    fun `isMainProcess if process resource blank`() {
        every { context.getString(any()) } returns " "
        assertTrue(context.isMainProcess(testPackageName))
    }

    @Test
    fun `isMainProcess if process resource testPackageName`() {
        every { context.getString(any()) } returns testPackageName
        assertTrue(context.isMainProcess(testPackageName))
    }

    @Test
    fun `isMainProcess if process resource start with testPackageName`() {
        every { context.getString(any()) } returns testPackageName + "sdfdsf"
        assertTrue(context.isMainProcess(testPackageName + ":" + testPackageName + "sdfdsf"))
    }

    @Test
    fun `isMainProcess if process resource text`() {
        every { context.getString(any()) } returns "myprocess"
        assertTrue(context.isMainProcess(customProcessName))
    }

    @Test
    fun `isMainProcess if process resource text start with two dots `() {
        every { context.getString(any()) } returns ":myprocess"
        assertTrue(context.isMainProcess(customProcessName))
    }

    @Test
    fun `isMainProcess if process resource text start with package name`() {
        every { context.getString(any()) } returns "com.test.app:myprocess"
        assertTrue(context.isMainProcess(customProcessName))
    }

    @Test
    fun `isMainProcess if process resource text start with text dot`() {
        mockkObject(Context::getCurrentProcessName)
        every { context.getString(any()) } returns "test.myprocess"
        assertTrue(context.isMainProcess("test.myprocess"))
    }

    @Test
    fun `add unique`() {
        val linkedList: Queue<String> = LinkedList()

        assertTrue(linkedList.addUnique("test1") { it == "test1" })
        assertTrue(linkedList.contains("test1"))
        assertEquals(1, linkedList.size)

        assertFalse(linkedList.addUnique("test1"))
        assertTrue(linkedList.contains("test1"))
        assertEquals(1, linkedList.size)

        assertTrue(linkedList.addUnique("test2"))
        assertTrue(linkedList.contains("test2"))
        assertEquals(2, linkedList.size)

        assertFalse(linkedList.addUnique("test3") { it.startsWith("test") })
        assertFalse(linkedList.contains("test3"))
        assertEquals(2, linkedList.size)

        assertTrue(linkedList.addUnique("tE5t4") { it.contains("tE5t4") })
        assertTrue(linkedList.contains("tE5t4"))
        assertEquals(3, linkedList.size)
    }

    @Test
    fun `should return empty string if error has no network response data`() {
        val error = VolleyError()
        assertEquals("", error.getErrorResponseBodyData())
    }

    @Test
    fun `should return response body data`() {
        val responseBodyData = "test string"
        val networkResponse = NetworkResponse(
            400,
            responseBodyData.toByteArray(),
            true,
            200,
            emptyList()
        )
        val error = VolleyError(networkResponse)
        assertEquals(responseBodyData, error.getErrorResponseBodyData())
    }

    @Test
    fun `isUuid returns true for valid UUID`() {
        val validUuid = "123e4567-e89b-12d3-a456-426614174000"
        assertTrue(validUuid.isUuid())
    }

    @Test
    fun `isUuid returns false for invalid UUID`() {
        val invalidUuid = "123-e89b-12d3-426614174000"
        assertFalse(invalidUuid.isUuid())
    }

    @Test
    fun `isUuid returns false for empty string`() {
        val emptyString = ""
        assertFalse(emptyString.isUuid())
    }

    @Test
    fun `isUuid returns false for blank string`() {
        val blankString = "   "
        assertFalse(blankString.isUuid())
    }

    @Test
    fun `isUuid returns false when UUID string is too long`() {
        val longUuid = "123e4567-e89b-12d3-a456-426614174000-extra-uuid"
        assertFalse(longUuid.isUuid())
    }

    @Test
    fun `putMindboxPushButtonExtras sets both push key and button key`() {
        val intent = Intent()
        val pushUniqKey = "testPushKey"
        val pushButtonKey = "testButtonKey"

        intent.putMindboxPushButtonExtras(pushUniqKey, pushButtonKey)

        assertEquals(pushUniqKey, intent.getStringExtra("uniq_push_key"))
        assertEquals(pushButtonKey, intent.getStringExtra("uniq_push_button_key"))
    }

    @Test
    fun `putMindboxPushButtonExtras sets both push key and button key is null`() {
        val intent = Intent()
        val pushUniqKey = "testPushKey"

        intent.putMindboxPushButtonExtras(pushUniqKey, null)

        assertEquals(pushUniqKey, intent.getStringExtra("uniq_push_key"))
        assertEquals(null, intent.getStringExtra("uniq_push_button_key"))
    }

    @Test
    fun `putMindboxPushExtras sets only push key and does not set button key`() {
        val intent = Intent()
        val pushUniqKey = "testPushKey"

        intent.putMindboxPushExtras(pushUniqKey)

        assertEquals(pushUniqKey, intent.getStringExtra("uniq_push_key"))
        assertNull("Button key should not be set", intent.getStringExtra("uniq_push_button_key"))
    }

    @Test
    fun `getMindboxUniqKeyFromPushIntent returns correct key when present`() {
        val expectedKey = "testPushKey"
        val intent = Intent().apply {
            putExtra("uniq_push_key", expectedKey)
        }

        val result = intent.getMindboxUniqKeyFromPushIntent()

        assertEquals(expectedKey, result)
    }

    @Test
    fun `getMindboxUniqKeyFromPushIntent returns null when key not present`() {
        val intent = Intent()

        val result = intent.getMindboxUniqKeyFromPushIntent()

        assertNull(result)
    }

    @Test
    fun `getMindboxUniqPushButtonKeyFromPushIntent returns correct button key when present`() {
        val expectedButtonKey = "testButtonKey"
        val intent = Intent().apply {
            putExtra("uniq_push_button_key", expectedButtonKey)
        }

        val result = intent.getMindboxUniqPushButtonKeyFromPushIntent()

        assertEquals(expectedButtonKey, result)
    }

    @Test
    fun `getMindboxUniqPushButtonKeyFromPushIntent returns null when button key not present`() {
        val intent = Intent()

        val result = intent.getMindboxUniqPushButtonKeyFromPushIntent()

        assertNull(result)
    }

    @Test
    fun `pollIf should return and remove element when predicate is true`() {
        val queue: Queue<Int> = LinkedList(listOf(1, 2, 3))
        val initialSize = queue.size

        val result = queue.pollIf { it == 1 }

        assertEquals(1, result)
        assertEquals(2, queue.peek())
        assertEquals(initialSize - 1, queue.size)
    }

    @Test
    fun `pollIf should return null and not remove element when predicate is false`() {
        val queue: Queue<Int> = LinkedList(listOf(1, 2, 3))
        val initialSize = queue.size

        val result = queue.pollIf { it == 2 }

        assertNull(result)
        assertEquals(initialSize, queue.size)
        assertEquals(1, queue.peek())
    }

    @Test
    fun `pollIf should return null for an empty queue`() {
        val queue: Queue<Int> = LinkedList()

        val result = queue.pollIf { true }

        assertNull(result)
        assertEquals(0, queue.size)
    }

    @Test
    fun `pollIf on queue with one element when predicate is true`() {
        val queue: Queue<Int> = LinkedList(listOf(5))

        val result = queue.pollIf { it == 5 }

        assertEquals(5, result)
        assertEquals(0, queue.size)
    }

    @Test
    fun `pollIf on queue with one element when predicate is false`() {
        val queue: Queue<Int> = LinkedList(listOf(5))

        val result = queue.pollIf { it != 5 }

        assertNull(result)
        assertEquals(1, queue.size)
    }

    @Test
    fun `pollIf with multiple calls`() {
        val queue: Queue<String> = LinkedList(listOf("apple", "banana", "cherry"))

        val appleResult = queue.pollIf { it.startsWith("a") }
        assertEquals("apple", appleResult)
        assertEquals(2, queue.size)

        val nonBBfruitResult = queue.pollIf { it.startsWith("b") }
        assertEquals("banana", nonBBfruitResult)
        assertEquals(1, queue.size)

        val cherryResult = queue.pollIf { it.length == 6 }
        assertEquals("cherry", cherryResult)
        assertEquals(0, queue.size)
    }

    @Test
    fun `toUrlQueryString should return empty string for empty map`() {
        val emptyMap = linkedMapOf<String, String>()
        val result = emptyMap.toUrlQueryString()
        assertEquals("?", result)
    }

    @Test
    fun `toUrlQueryString should return correct query string for single key-value pair`() {
        val map = linkedMapOf("key" to "value")
        val result = map.toUrlQueryString()
        assertEquals("?key=value", result)
    }

    @Test
    fun `toUrlQueryString should return correct query string for multiple key-value pairs`() {
        val map = linkedMapOf(
            "key1" to "value1",
            "key2" to "value2",
            "key3" to "value3"
        )
        val result = map.toUrlQueryString()
        assertEquals("?key1=value1&key2=value2&key3=value3", result)
    }

    @Test
    fun `toUrlQueryString should handle special characters in keys and values`() {
        val map = linkedMapOf(
            "key with spaces" to "value with spaces",
            "key-with-dashes" to "value-with-dashes",
            "key_with_underscores" to "value_with_underscores"
        )
        val result = map.toUrlQueryString()
        assertEquals("?key+with+spaces=value+with+spaces&key-with-dashes=value-with-dashes&key_with_underscores=value_with_underscores", result)
    }

    @Test
    fun `toUrlQueryString should handle empty values`() {
        val map = linkedMapOf(
            "key1" to "",
            "key2" to "value2"
        )
        val result = map.toUrlQueryString()
        assertEquals("?key1=&key2=value2", result)
    }

    @Test
    fun `toUrlQueryString should handle special URL characters`() {
        val map = linkedMapOf(
            "param1" to "value=with=equals",
            "param2" to "value&with&ampersands",
            "param3" to "value?with?question?marks"
        )
        val result = map.toUrlQueryString()
        assertEquals("?param1=value%3Dwith%3Dequals&param2=value%26with%26ampersands&param3=value%3Fwith%3Fquestion%3Fmarks", result)
    }

    @Test
    fun `toUrlQueryString should handle numeric values as strings`() {
        val map = linkedMapOf(
            "number" to "123",
            "decimal" to "123.45",
            "negative" to "-123"
        )
        val result = map.toUrlQueryString()
        assertEquals("?number=123&decimal=123.45&negative=-123", result)
    }

    @Test
    fun `toUrlQueryString should handle unicode characters`() {
        val map = linkedMapOf(
            "russian" to "привет",
            "chinese" to "你好",
            "emoji" to "🚀"
        )
        val result = map.toUrlQueryString()
        assertEquals("?russian=%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82&chinese=%E4%BD%A0%E5%A5%BD&emoji=%F0%9F%9A%80", result)
    }
}
