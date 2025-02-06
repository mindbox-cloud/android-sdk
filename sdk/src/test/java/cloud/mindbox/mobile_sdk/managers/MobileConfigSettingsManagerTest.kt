package cloud.mindbox.mobile_sdk.managers
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.models.SettingsStub
import cloud.mindbox.mobile_sdk.utils.SystemTimeProvider
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MobileConfigSettingsManagerImplTest {

    private lateinit var sessionStorageManager: SessionStorageManager
    private lateinit var mobileConfigSettingsManager: MobileConfigSettingsManagerImpl

    @Before
    fun onTestStart() {
        val realSessionStorageManager = SessionStorageManager(SystemTimeProvider())
        sessionStorageManager = spyk(realSessionStorageManager)
        mobileConfigSettingsManager = MobileConfigSettingsManagerImpl(sessionStorageManager)
    }

    @Test
    fun `saveSessionTime set sessionTime when slidingExpiration config is valid`() {
        val config = SettingsStub.getSlidingExpiration("0:0:0.1")

        mobileConfigSettingsManager.saveSessionTime(config)

        assertEquals(100L, sessionStorageManager.sessionTime.inWholeMilliseconds)
    }

    @Test
    fun `saveSessionTime doesn't set sessionTime when sessionTime is zero`() {
        val config = SettingsStub.getSlidingExpiration("0:0:0.0")

        mobileConfigSettingsManager.saveSessionTime(config)

        verify(exactly = 0) { sessionStorageManager.sessionTime = any() }
        assertEquals(0L, sessionStorageManager.sessionTime.inWholeMilliseconds)
    }

    @Test
    fun `saveSessionTime doesn't set sessionTime when sessionTime is negative`() {
        val config = SettingsStub.getSlidingExpiration("-0:0:0.001")

        mobileConfigSettingsManager.saveSessionTime(config)

        verify(exactly = 0) { sessionStorageManager.sessionTime = any() }
        assertEquals(0L, sessionStorageManager.sessionTime.inWholeMilliseconds)
    }
}
