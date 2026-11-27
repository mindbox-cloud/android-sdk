package cloud.mindbox.mobile_sdk.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.pushes.MindboxPushService
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.pushes.PushServiceRegistry
import cloud.mindbox.mobile_sdk.pushes.contextWithMetaData
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
            every { isServiceAvailable(any()) } returns isAvailable
            coEvery { initService(any()) } just runs
        }

    private fun service(handler: PushServiceHandler): MindboxPushService = object : MindboxPushService {
        override val tag: String = handler.notificationProvider

        override fun getServiceHandler(logger: MindboxLogger, exceptionHandler: ExceptionHandler) = handler
    }

    private val firebase = handler("FCM", "google")
    private val huawei = handler("HCM", "huawei")
    private val ruStore = handler("RuStore", null)

    private val huaweiMetaData = "cloud.mindbox.push.provider.huawei" to HuaweiTrackingService::class.java.name

    private val registry = PushServiceRegistry { CoroutineScope(SupervisorJob() + Dispatchers.Unconfined) }

    private fun registryTypes(): Set<String>? = trackingIdTypesOf(registry.handlers, registry.isSettled)

    @Test
    fun `nothing passed to init yet leaves the configuration unknown`() {
        assertNull(registryTypes())
    }

    @Test
    fun `an empty list leaves the configuration unknown until init reads the manifest`() = runTest {
        registry.registerExplicit(emptyList())

        assertNull(registryTypes())

        registry.initialize(contextWithMetaData(huaweiMetaData))

        assertEquals(setOf("huawei"), registryTypes())
    }

    @Test
    fun `an empty list passed after the push services keeps their types`() {
        registry.registerExplicit(listOf(service(firebase)))
        registry.registerExplicit(emptyList())

        assertEquals(setOf("google"), registryTypes())
    }

    @Test
    fun `push services that are not settled yet leave the configuration unknown`() {
        assertNull(trackingIdTypesOf(listOf(firebase, huawei), isSettled = false))
    }

    @Test
    fun `every passed push service with a tracking id is configured`() {
        val types = trackingIdTypesOf(listOf(firebase, huawei, ruStore), isSettled = true)

        assertEquals(setOf("google", "huawei"), types)
    }

    @Test
    fun `a RuStore-only integration configures no tracking id types`() {
        val types = trackingIdTypesOf(listOf(ruStore), isSettled = true)

        assertEquals(emptySet<String>(), types)
    }

    @Test
    fun `a service filtered out as unavailable at startup stays configured`() = runTest {
        val unavailableFirebase = handler("FCM", "google", isAvailable = false)
        registry.registerExplicit(listOf(service(unavailableFirebase)))
        registry.initialize(mockk<Context>())

        assertEquals(setOf("google"), registryTypes())
    }

    @Test
    fun `a service counts even if it came from another init call`() {
        registry.registerExplicit(listOf(service(firebase)))
        registry.registerExplicit(listOf(service(firebase), service(huawei)))

        assertEquals(setOf("google", "huawei"), registryTypes())
    }

    @Test
    fun `services found in the manifest stay unknown until init is called`() {
        registry.resolveHandlers(contextWithMetaData(huaweiMetaData))

        assertNull(registryTypes())
    }

    @Test
    fun `services found in the manifest are configured once init is called`() = runTest {
        registry.initialize(contextWithMetaData(huaweiMetaData))

        assertEquals(setOf("huawei"), registryTypes())
    }

    @Test
    fun `a manifest without provider modules leaves the configuration unknown until init is called`() {
        registry.resolveHandlers(contextWithMetaData())

        assertNull(registryTypes())
    }

    @Test
    fun `init without a list in an app without provider modules configures no tracking id types`() = runTest {
        registry.initialize(contextWithMetaData())

        assertEquals(emptySet<String>(), registryTypes())
    }
}

internal object HuaweiTrackingService : MindboxPushService {
    override val tag: String = "HCM"

    override fun getServiceHandler(logger: MindboxLogger, exceptionHandler: ExceptionHandler): PushServiceHandler =
        mockk {
            every { notificationProvider } returns "HCM"
            every { trackingIdType } returns "huawei"
            every { isServiceAvailable(any()) } returns true
            coEvery { initService(any()) } just runs
        }
}
