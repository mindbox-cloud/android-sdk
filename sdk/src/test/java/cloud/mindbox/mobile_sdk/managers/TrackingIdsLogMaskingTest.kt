package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.MindboxRequest
import cloud.mindbox.mobile_sdk.network.MindboxServiceGenerator
import cloud.mindbox.mobile_sdk.repository.MindboxDatabase
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val GOOGLE_VALUE = "38400000-8cf0-11bd-b23e-10b96e40000d"
private const val MASKED_GOOGLE_VALUE = "38400000-****-****-****-************"
private const val IDS_BODY = """{"instanceId":"i","ids":[{"type":"google","value":"$GOOGLE_VALUE"}]}"""

class TrackingIdsLogMaskingTest {

    private val logged = mutableListOf<String>()

    @Before
    fun setUp() {
        mockkObject(MindboxLoggerImpl)
        every { MindboxLoggerImpl.d(any(), capture(logged)) } just runs
        every { MindboxLoggerImpl.i(any(), capture(logged)) } just runs
        every { MindboxLoggerImpl.e(any(), capture(logged)) } just runs
        every { MindboxLoggerImpl.e(any(), capture(logged), any()) } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun assertOnlyMaskedValueIsLogged() {
        assertTrue(logged.joinToString("\n"), logged.any { MASKED_GOOGLE_VALUE in it })
        assertFalse(logged.joinToString("\n"), logged.any { GOOGLE_VALUE in it })
    }

    @Test
    fun `the logged request body has the tracking ids masked`() {
        val body = mockk<JSONObject>()
        every { body.toString() } returns IDS_BODY
        val request = MindboxRequest(
            fullUrl = "https://api-tracking.mindbox.ru/v3/operations/async",
            configuration = mockk(relaxed = true),
            jsonRequest = body,
        )

        MindboxServiceGenerator(mockk(relaxed = true)).addToRequestQueue(request)

        assertOnlyMaskedValueIsLogged()
    }

    @Test
    fun `the request as Volley prints it carries neither the body nor the listeners`() {
        val body = mockk<JSONObject>()
        every { body.toString() } returns IDS_BODY
        val request = MindboxRequest(
            fullUrl = "https://api-tracking.mindbox.ru/v3/operations/async",
            configuration = mockk(relaxed = true),
            jsonRequest = body,
            listener = {},
            errorsListener = {},
        )

        assertEquals("MindboxRequest(methodType=1, fullUrl=https://api-tracking.mindbox.ru/v3/operations/async)", request.toString())
    }

    @Test
    fun `the event that failed to be written to the database is logged with the tracking ids masked`() {
        val database = mockk<MindboxDatabase>()
        every { database.eventsDao().insert(any()) } throws IllegalStateException("database is closed")
        mockkObject(BackgroundWorkManager)
        every { BackgroundWorkManager.startOneTimeService(any()) } just runs
        val databaseField = DbManager::class.java.getDeclaredField("mindboxDb").apply { isAccessible = true }
        val originalDatabase = databaseField.get(DbManager)
        databaseField.set(DbManager, database)

        try {
            DbManager.addEventToQueue(mockk<Context>(), Event(eventType = EventType.AppTrackingIdsUpdated, body = IDS_BODY))
        } finally {
            databaseField.set(DbManager, originalDatabase)
        }

        assertOnlyMaskedValueIsLogged()
    }
}
