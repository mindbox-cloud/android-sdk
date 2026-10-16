package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.pushes.MindboxPushService
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.pushes.PushServiceRegistry
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackingIdTypesOfTest {

    private fun handler(tag: String, trackingIdType: String?, isAvailable: Boolean = true): PushServiceHandler =
        mockk {
            every { notificationProvider } returns tag
            every { this@mockk.trackingIdType } returns trackingIdType
            every { isServiceAvailable(any(), any()) } returns isAvailable
            coEvery { initService(any()) } just runs
        }

    private fun service(handler: PushServiceHandler): MindboxPushService = object : MindboxPushService {
        override val tag: String = handler.notificationProvider

        override fun getServiceHandler(logger: MindboxLogger, exceptionHandler: ExceptionHandler) = handler
    }

    private val firebase = handler("FCM", "google")
    private val huawei = handler("HCM", "huawei")
    private val ruStore = handler("RuStore", null)

    private val registry = PushServiceRegistry { CoroutineScope(SupervisorJob() + Dispatchers.Unconfined) }

    @Test
    fun `nothing passed to init yet leaves the configuration unknown`() {
        assertNull(trackingIdTypesOf(emptyList()))
    }

    @Test
    fun `every passed push service with a tracking id is configured`() {
        val types = trackingIdTypesOf(listOf(firebase, huawei, ruStore))

        assertEquals(setOf("google", "huawei"), types)
    }

    @Test
    fun `a RuStore-only integration configures no tracking id types`() {
        val types = trackingIdTypesOf(listOf(ruStore))

        assertEquals(emptySet<String>(), types)
    }

    @Test
    fun `a service filtered out as unavailable at startup stays configured`() = runTest {
        val unavailableFirebase = handler("FCM", "google", isAvailable = false)
        registry.registerExplicit(listOf(service(unavailableFirebase)))
        registry.initializeAvailable(mockk<Context>())

        assertEquals(setOf("google"), trackingIdTypesOf(registry.handlers))
    }

    @Test
    fun `a service counts even if it came from another init call`() {
        registry.registerExplicit(listOf(service(firebase)))
        registry.registerExplicit(listOf(service(firebase), service(huawei)))

        assertEquals(setOf("google", "huawei"), trackingIdTypesOf(registry.handlers))
    }
}
