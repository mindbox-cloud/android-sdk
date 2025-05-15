package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.models.SettingsStub.Companion.getSlidingExpiration
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import cloud.mindbox.mobile_sdk.models.operation.response.SettingsDto
import cloud.mindbox.mobile_sdk.pushes.PushNotificationManager
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.SystemTimeProvider
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MobileConfigSettingsManagerImplTest {

    private lateinit var sessionStorageManager: SessionStorageManager
    private lateinit var mobileConfigSettingsManager: MobileConfigSettingsManagerImpl
    private val now = 100_000L

    @Before
    fun onTestStart() {
        val realSessionStorageManager = SessionStorageManager(SystemTimeProvider())
        sessionStorageManager = spyk(realSessionStorageManager)
        mobileConfigSettingsManager = MobileConfigSettingsManagerImpl(mockk(), sessionStorageManager, object : TimeProvider {
            override fun currentTimeMillis(): Long = now
        })
        mockkObject(Mindbox)
        mockkObject(MindboxPreferences)
        mockkObject(MindboxEventManager)
        mockkObject(PushNotificationManager)
        every { MindboxEventManager.appKeepAlive(any(), any()) } just runs
    }

    @After
    fun onTestEnd() {
        unmockkObject(MindboxPreferences)
        unmockkObject(MindboxEventManager)
        unmockkObject(PushNotificationManager)
    }

    @Test
    fun `saveSessionTime set sessionTime when slidingExpiration config is valid`() {
        val config = getSlidingExpiration(timeSpan = "0:0:0.1")

        mobileConfigSettingsManager.saveSessionTime(config)

        assertEquals(100L, sessionStorageManager.sessionTime.inWholeMilliseconds)
    }

    @Test
    fun `saveSessionTime doesn't set sessionTime when sessionTime is zero`() {
        val config = getSlidingExpiration(timeSpan = "0:0:0.0")

        mobileConfigSettingsManager.saveSessionTime(config)

        verify(exactly = 0) { sessionStorageManager.sessionTime = any() }
        assertEquals(0L, sessionStorageManager.sessionTime.inWholeMilliseconds)
    }

    @Test
    fun `saveSessionTime doesn't set sessionTime when sessionTime is negative`() {
        val config = getSlidingExpiration(timeSpan = "-0:0:0.001")

        mobileConfigSettingsManager.saveSessionTime(config)

        verify(exactly = 0) { sessionStorageManager.sessionTime = any() }
        assertEquals(0L, sessionStorageManager.sessionTime.inWholeMilliseconds)
    }

    @Test
    fun `checkPushTokenKeepALive sends keepAlive when lastInfoUpdateTime is null`() = runTest {
        every { MindboxPreferences.lastInfoUpdateTime } returns null
        every { Mindbox.mindboxScope } returns backgroundScope

        val config = getSlidingExpiration(pushTokenKeepALive = "0:0:10.0")
        mobileConfigSettingsManager.checkPushTokenKeepALive(config)

        Thread.sleep(1000L)
        verify(exactly = 1) { MindboxEventManager.appKeepAlive(any(), any()) }
    }

    @Test
    fun `checkPushTokenKeepALive does not send appKeepAlive when not expired`() {
        every { MindboxPreferences.lastInfoUpdateTime } returns (now - 5_000L)
        val config = getSlidingExpiration(pushTokenKeepALive = "0:0:10.0")
        mobileConfigSettingsManager.checkPushTokenKeepALive(config)

        verify(exactly = 0) { MindboxEventManager.appKeepAlive(any(), any()) }
    }

    @Test
    fun `checkPushTokenKeepALive sends keepAlive when expired`() = runTest {
        every { MindboxPreferences.lastInfoUpdateTime } returns (now - 20_000L)
        every { Mindbox.mindboxScope } returns backgroundScope
        val config = getSlidingExpiration(pushTokenKeepALive = "0:0:10.0")
        mobileConfigSettingsManager.checkPushTokenKeepALive(config)

        verify(exactly = 1) { MindboxEventManager.appKeepAlive(any(), any()) }
    }

    @Test
    fun `checkPushTokenKeepALive not sends when pushTokenKeepALive is not set`() {
        val config = getSlidingExpiration(pushTokenKeepALive = null)
        every { MindboxPreferences.lastInfoUpdateTime } returns now
        mobileConfigSettingsManager.checkPushTokenKeepALive(config)

        verify(exactly = 0) { MindboxEventManager.appKeepAlive(any(), any()) }
    }

    @Test
    fun `checkPushTokenKeepALive not sends when pushTokenKeepALive is invalid`() {
        every { MindboxPreferences.lastInfoUpdateTime } returns now
        val config = getSlidingExpiration(pushTokenKeepALive = "now")
        mobileConfigSettingsManager.checkPushTokenKeepALive(config)

        verify(exactly = 0) { MindboxEventManager.appKeepAlive(any(), any()) }
    }

    @Test
    fun `checkPushTokenKeepALive not sends when pushTokenKeepALive is less zero`() {
        every { MindboxPreferences.lastInfoUpdateTime } returns now
        val config = getSlidingExpiration(pushTokenKeepALive = "-0:0:10.0")
        mobileConfigSettingsManager.checkPushTokenKeepALive(config)

        verify(exactly = 0) { MindboxEventManager.appKeepAlive(any(), any()) }
    }

    @Test
    fun `checkPushTokenKeepALive not sends when pushTokenKeepALive is zero`() {
        every { MindboxPreferences.lastInfoUpdateTime } returns now
        val config = getSlidingExpiration(pushTokenKeepALive = "0:0:0.0")
        mobileConfigSettingsManager.checkPushTokenKeepALive(config)

        verify(exactly = 0) { MindboxEventManager.appKeepAlive(any(), any()) }
    }

    @Test
    fun `checkPushTokenKeepALive not sends when settings is null`() {
        every { MindboxPreferences.lastInfoUpdateTime } returns now
        val config = InAppConfigResponse(null, null, null, null)
        mobileConfigSettingsManager.checkPushTokenKeepALive(config)

        verify(exactly = 0) { MindboxEventManager.appKeepAlive(any(), any()) }
    }

    @Test
    fun `checkPushTokenKeepALive not sends when SlidingExpiration is null`() {
        every { MindboxPreferences.lastInfoUpdateTime } returns now
        val config = InAppConfigResponse(null, null, SettingsDto(null, null, null), null)
        mobileConfigSettingsManager.checkPushTokenKeepALive(config)

        verify(exactly = 0) { MindboxEventManager.appKeepAlive(any(), any()) }
    }
}
