package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.models.Milliseconds
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
        every { MindboxEventManager.appKeepalive(any(), any()) } just runs
    }

    @After
    fun onTestEnd() {
        unmockkObject(MindboxPreferences)
        unmockkObject(MindboxEventManager)
        unmockkObject(PushNotificationManager)
    }

    @Test
    fun `saveSessionTime set sessionTime when slidingExpiration config is valid`() {
        val config = getSlidingExpiration(config = Milliseconds(100L))

        mobileConfigSettingsManager.saveSessionTime(config)

        assertEquals(100L, sessionStorageManager.sessionTime.inWholeMilliseconds)
    }

    @Test
    fun `saveSessionTime doesn't set sessionTime when sessionTime is zero`() {
        val config = getSlidingExpiration(config = Milliseconds(0L))

        mobileConfigSettingsManager.saveSessionTime(config)

        verify(exactly = 0) { sessionStorageManager.sessionTime = any() }
        assertEquals(0L, sessionStorageManager.sessionTime.inWholeMilliseconds)
    }

    @Test
    fun `saveSessionTime doesn't set sessionTime when sessionTime is negative`() {
        val config = getSlidingExpiration(Milliseconds(-1L))

        mobileConfigSettingsManager.saveSessionTime(config)

        verify(exactly = 0) { sessionStorageManager.sessionTime = any() }
        assertEquals(0L, sessionStorageManager.sessionTime.inWholeMilliseconds)
    }

    @Test
    fun `checkPushTokenKeepalive sends keepAlive when lastInfoUpdateTime is null`() = runTest {
        every { MindboxPreferences.lastInfoUpdateTime } returns null
        every { Mindbox.mindboxScope } returns backgroundScope

        val config = getSlidingExpiration(pushTokenKeepalive = Milliseconds(10000L))
        mobileConfigSettingsManager.checkPushTokenKeepalive(config)
        Thread.sleep(1000L)
        verify(exactly = 1) { MindboxEventManager.appKeepalive(any(), any()) }
    }

    @Test
    fun `checkPushTokenKeepalive does not send appKeepalive when not expired`() {
        every { MindboxPreferences.lastInfoUpdateTime } returns (now - 5_000L)
        val config = getSlidingExpiration(pushTokenKeepalive = Milliseconds(10000L))
        mobileConfigSettingsManager.checkPushTokenKeepalive(config)

        verify(exactly = 0) { MindboxEventManager.appKeepalive(any(), any()) }
    }

    @Test
    fun `checkPushTokenKeepalive sends keepAlive when expired`() = runTest {
        every { MindboxPreferences.lastInfoUpdateTime } returns (now - 20_000L)
        every { Mindbox.mindboxScope } returns backgroundScope
        val config = getSlidingExpiration(pushTokenKeepalive = Milliseconds(10000L))
        mobileConfigSettingsManager.checkPushTokenKeepalive(config)

        verify(exactly = 1) { MindboxEventManager.appKeepalive(any(), any()) }
    }

    @Test
    fun `checkPushTokenKeepalive not sends when pushTokenKeepalive is not set`() {
        val config = getSlidingExpiration(pushTokenKeepalive = null)
        every { MindboxPreferences.lastInfoUpdateTime } returns now
        mobileConfigSettingsManager.checkPushTokenKeepalive(config)

        verify(exactly = 0) { MindboxEventManager.appKeepalive(any(), any()) }
    }

    @Test
    fun `checkPushTokenKeepalive not sends when pushTokenKeepalive is less zero`() {
        every { MindboxPreferences.lastInfoUpdateTime } returns now
        val config = getSlidingExpiration(pushTokenKeepalive = Milliseconds(-5))
        mobileConfigSettingsManager.checkPushTokenKeepalive(config)

        verify(exactly = 0) { MindboxEventManager.appKeepalive(any(), any()) }
    }

    @Test
    fun `checkPushTokenKeepalive not sends when pushTokenKeepalive is zero`() {
        every { MindboxPreferences.lastInfoUpdateTime } returns now
        val config = getSlidingExpiration(pushTokenKeepalive = Milliseconds(0))
        mobileConfigSettingsManager.checkPushTokenKeepalive(config)

        verify(exactly = 0) { MindboxEventManager.appKeepalive(any(), any()) }
    }

    @Test
    fun `checkPushTokenKeepalive not sends when settings is null`() {
        every { MindboxPreferences.lastInfoUpdateTime } returns now
        val config = InAppConfigResponse(null, null, null, null)
        mobileConfigSettingsManager.checkPushTokenKeepalive(config)

        verify(exactly = 0) { MindboxEventManager.appKeepalive(any(), any()) }
    }

    @Test
    fun `checkPushTokenKeepalive not sends when SlidingExpiration is null`() {
        every { MindboxPreferences.lastInfoUpdateTime } returns now
        val config = InAppConfigResponse(null, null, SettingsDto(null, null, null, null), null)
        mobileConfigSettingsManager.checkPushTokenKeepalive(config)

        verify(exactly = 0) { MindboxEventManager.appKeepalive(any(), any()) }
    }
}
