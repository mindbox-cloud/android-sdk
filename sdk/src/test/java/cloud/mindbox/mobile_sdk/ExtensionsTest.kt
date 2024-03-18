package cloud.mindbox.mobile_sdk

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.presentation.MindboxNotificationManager
import cloud.mindbox.mobile_sdk.inapp.presentation.actions.PushPermissionInAppAction
import cloud.mindbox.mobile_sdk.inapp.presentation.actions.RedirectUrlInAppAction
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
    fun `hasImageLayerWithRedirectUrlAction returns true for Snackbar with RedirectUrlAction`() {
        val redirectAction = mockk<Layer.ImageLayer.Action.RedirectUrlAction>()

        val imageLayer = mockk<Layer.ImageLayer> {
            every { action } returns redirectAction
        }

        val snackbar = mockk<InAppType.Snackbar> {
            every { layers } returns listOf(imageLayer)
        }

        assertTrue(snackbar.hasImageLayerWithRedirectUrlAction())
    }

    @Test
    fun `hasImageLayerWithRedirectUrlAction returns false for Snackbar with PushPermissionAction`() {
        val pushPermissionAction = mockk<Layer.ImageLayer.Action.PushPermissionAction>()

        val imageLayer = mockk<Layer.ImageLayer> {
            every { action } returns pushPermissionAction
        }

        val snackbar = mockk<InAppType.Snackbar> {
            every { layers } returns listOf(imageLayer)
        }

        assertFalse(snackbar.hasImageLayerWithRedirectUrlAction())
    }

    @Test
    fun `hasImageLayerWithRedirectUrlAction returns false for Snackbar with empty layers`() {

        val snackbar = mockk<InAppType.Snackbar> {
            every { layers } returns emptyList()
        }

        assertFalse(snackbar.hasImageLayerWithRedirectUrlAction())
    }

    @Test
    fun `hasImageLayerWithRedirectUrlAction returns true for Modal with RedirectUrlAction`() {
        val redirectAction = mockk<Layer.ImageLayer.Action.RedirectUrlAction>()

        val imageLayer = mockk<Layer.ImageLayer> {
            every { action } returns redirectAction
        }

        val modal = mockk<InAppType.Snackbar> {
            every { layers } returns listOf(imageLayer)
        }

        assertTrue(modal.hasImageLayerWithRedirectUrlAction())
    }

    @Test
    fun `hasImageLayerWithRedirectUrlAction returns false for Modal with PushPermissionAction`() {
        val pushPermissionAction = mockk<Layer.ImageLayer.Action.PushPermissionAction>()

        val imageLayer = mockk<Layer.ImageLayer> {
            every { action } returns pushPermissionAction
        }

        val modal = mockk<InAppType.Snackbar> {
            every { layers } returns listOf(imageLayer)
        }

        assertFalse(modal.hasImageLayerWithRedirectUrlAction())
    }

    @Test
    fun `hasImageLayerWithRedirectUrlAction returns false for Modal with empty layers`() {

        val modal = mockk<InAppType.Snackbar> {
            every { layers } returns emptyList()
        }

        assertFalse(modal.hasImageLayerWithRedirectUrlAction())
    }

    @Test
    fun `createAction returns RedirectUrlInAppAction for RedirectUrlAction`() {

        val mindboxNotificationManager = mockk<MindboxNotificationManager>()
        val redirectUrlAction =
            Layer.ImageLayer.Action.RedirectUrlAction(url = "test_url", payload = "test_payload")

        val resultAction = redirectUrlAction.createAction(mindboxNotificationManager)

        assertTrue(resultAction is RedirectUrlInAppAction)
        resultAction as RedirectUrlInAppAction
        assertTrue(resultAction.url == "test_url")
        assertTrue(resultAction.payload == "test_payload")
    }

    @Test
    fun `createAction returns PushPermissionInAppAction for PushPermissionAction`() {
        val mindboxNotificationManager = mockk<MindboxNotificationManager>()
        val pushPermissionAction =
            Layer.ImageLayer.Action.PushPermissionAction(payload = "test_payload")

        val resultAction = pushPermissionAction.createAction(mindboxNotificationManager)

        assertTrue(resultAction is PushPermissionInAppAction)
        resultAction as PushPermissionInAppAction
        assertTrue(resultAction.payload == "test_payload")
        assertTrue(resultAction.mindboxNotificationManager == mindboxNotificationManager)
    }
}
