package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.models.TokenData
import cloud.mindbox.mobile_sdk.models.UpdateData
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.SystemTimeProvider
import cloud.mindbox.mobile_sdk.utils.setMindboxScope
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Test

class MindboxEventManagerKeepaliveTest {

    private val context = mockk<Context>()
    private val timeProvider = mockk<SystemTimeProvider> {
        every { currentTimeMillis() } returns 1_000L
    }
    private val updateData = UpdateData(
        isNotificationsEnabled = true,
        instanceId = "instanceId",
        version = 1,
        tokens = listOf(TokenData(token = "token", notificationProvider = "FCM")),
    )

    @Before
    fun setUp() {
        setMindboxScope(CoroutineScope(Job().apply { cancel() }))
        mockkObject(MindboxDI)
        every { MindboxDI.appModule } returns mockk(relaxed = true) {
            every { timeProvider } returns this@MindboxEventManagerKeepaliveTest.timeProvider
        }
        mockkObject(MindboxPreferences)
        every { MindboxPreferences.lastInfoUpdateTime = any() } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `appInfoUpdate moves the Keepalive clock by default`() {
        MindboxEventManager.appInfoUpdate(context, updateData)

        verify(exactly = 1) { MindboxPreferences.lastInfoUpdateTime = 1_000L }
    }

    @Test
    fun `appInfoUpdate about the ids alone leaves the Keepalive clock alone`() {
        MindboxEventManager.appInfoUpdate(context, updateData, refreshesKeepalive = false)

        verify(exactly = 0) { MindboxPreferences.lastInfoUpdateTime = any() }
    }

    @Test
    fun `appKeepalive moves the Keepalive clock`() {
        MindboxEventManager.appKeepalive(context, updateData)

        verify(exactly = 1) { MindboxPreferences.lastInfoUpdateTime = 1_000L }
    }
}
