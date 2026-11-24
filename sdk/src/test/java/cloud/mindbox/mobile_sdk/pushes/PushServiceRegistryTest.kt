package cloud.mindbox.mobile_sdk.pushes

import android.content.Context
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PushServiceRegistryTest {

    private val context = mockk<Context>()
    private val registry = PushServiceRegistry { CoroutineScope(SupervisorJob() + Dispatchers.Unconfined) }

    private val firebase = FakePushService("FCM")
    private val huawei = FakePushService("HCM")
    private val ruStore = FakePushService("RuStore")

    private class FakePushService(override val tag: String) : MindboxPushService {

        var isAvailable: Boolean = true

        val handler: PushServiceHandler = mockk {
            every { notificationProvider } returns tag
            every { isServiceAvailable(any()) } answers { isAvailable }
            coEvery { initService(any()) } just runs
        }

        override fun getServiceHandler(logger: MindboxLogger, exceptionHandler: ExceptionHandler): PushServiceHandler =
            handler
    }

    private val PushServiceRegistry.tags: List<String>
        get() = handlers.map { it.notificationProvider }

    private val PushServiceRegistry.initializedTags: List<String>
        get() = initializedHandlers.map { it.notificationProvider }

    @Test
    fun `nothing is in use before any registration`() {
        assertEquals(emptyList<String>(), registry.tags)
        assertFalse(registry.isRegistered("FCM"))
    }

    @Test
    fun `explicit list keeps its order`() {
        registry.registerExplicit(listOf(ruStore, firebase))

        assertEquals(listOf("RuStore", "FCM"), registry.tags)
    }

    @Test
    fun `explicit lists from several calls are merged in the order of first appearance`() {
        registry.registerExplicit(listOf(firebase))
        registry.registerExplicit(listOf(huawei, firebase))

        assertEquals(listOf("FCM", "HCM"), registry.tags)
        assertTrue(registry.isRegistered("HCM"))
    }

    @Test
    fun `the same list passed twice is not duplicated`() {
        registry.registerExplicit(listOf(firebase, huawei))
        registry.registerExplicit(listOf(firebase, huawei))

        assertEquals(listOf("FCM", "HCM"), registry.tags)
    }

    @Test
    fun `empty list means no push services`() {
        registry.registerExplicit(emptyList())

        assertEquals(emptyList<String>(), registry.tags)
        assertTrue(registry.isSettled)
    }

    @Test
    fun `non-empty list after an empty one decides the services`() {
        registry.registerExplicit(emptyList())

        registry.registerExplicit(listOf(ruStore))

        assertEquals(listOf("RuStore"), registry.tags)
    }

    @Test
    fun `empty list after a non-empty one keeps the services, as in the Flutter and React Native flow`() {
        registry.registerExplicit(listOf(firebase))
        registry.registerExplicit(emptyList())

        assertEquals(listOf("FCM"), registry.tags)
    }

    @Test
    fun `a service is registered as soon as the call returns, before it is initialized`() {
        registry.registerExplicit(listOf(firebase))

        assertTrue(registry.isRegistered("FCM"))
        assertEquals(emptyList<String>(), registry.initializedTags)
    }

    @Test
    fun `initialization skips unavailable services and keeps the order`() = runTest {
        huawei.isAvailable = false
        registry.registerExplicit(listOf(ruStore, huawei, firebase))

        assertTrue(registry.initialize(context))

        assertEquals(listOf("RuStore", "FCM"), registry.initializedTags)
        assertEquals(listOf("RuStore", "HCM", "FCM"), registry.tags)
        coVerify(exactly = 0) { huawei.handler.initService(any()) }
    }

    @Test
    fun `an unavailable service is checked again by the next init while no service is started`() = runTest {
        firebase.isAvailable = false
        registry.registerExplicit(listOf(firebase))
        registry.initialize(context)

        firebase.isAvailable = true

        assertTrue(registry.initialize(context))
        assertEquals(listOf("FCM"), registry.initializedTags)
    }

    @Test
    fun `an unavailable service is not checked again once another service is started`() = runTest {
        huawei.isAvailable = false
        registry.registerExplicit(listOf(firebase, huawei))
        registry.initialize(context)

        huawei.isAvailable = true

        assertFalse(registry.initialize(context))
        verify(exactly = 1) { huawei.handler.isServiceAvailable(any()) }
        assertEquals(listOf("FCM"), registry.initializedTags)
    }

    @Test
    fun `an unavailable service is not checked again when tokens are requested`() = runTest {
        firebase.isAvailable = false
        registry.registerExplicit(listOf(firebase))
        registry.initialize(context)

        firebase.isAvailable = true

        assertFalse(registry.initializePending(context))
        verify(exactly = 1) { firebase.handler.isServiceAvailable(any()) }
    }

    @Test
    fun `an unavailable service is started after its token marks it available`() = runTest {
        firebase.isAvailable = false
        registry.registerExplicit(listOf(firebase))
        registry.initialize(context)

        registry.markAvailable("FCM")

        assertTrue(registry.initializePending(context))
        assertEquals(listOf("FCM"), registry.initializedTags)
    }

    @Test
    fun `a list alone does not start its services`() = runTest {
        registry.registerExplicit(listOf(firebase))

        assertFalse(registry.initializePending(context))

        assertEquals(emptyList<String>(), registry.initializedTags)
        coVerify(exactly = 0) { firebase.handler.initService(any()) }
    }

    @Test
    fun `an initialized service is not initialized again`() = runTest {
        registry.registerExplicit(listOf(firebase))
        registry.initialize(context)

        assertFalse(registry.initialize(context))

        coVerify(exactly = 1) { firebase.handler.initService(any()) }
    }

    @Test
    fun `a service added by a later list is initialized on the next call`() = runTest {
        registry.registerExplicit(listOf(firebase))
        registry.initialize(context)

        registry.registerExplicit(listOf(firebase, huawei))

        assertTrue(registry.initialize(context))
        assertEquals(listOf("FCM", "HCM"), registry.initializedTags)
        coVerify(exactly = 1) { firebase.handler.initService(any()) }
    }

    @Test
    fun `a service that failed to initialize stays in use`() = runTest {
        coEvery { firebase.handler.initService(any()) } throws IllegalStateException("init failed")
        registry.registerExplicit(listOf(firebase))

        registry.initialize(context)

        assertEquals(listOf("FCM"), registry.initializedTags)
    }

    @Test
    fun `lists registered from many threads at once are all merged`() {
        val services = (1..32).map { FakePushService("provider$it") }
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val done = CountDownLatch(services.size)
        services.forEach { service ->
            executor.execute {
                start.await()
                registry.registerExplicit(listOf(service))
                done.countDown()
            }
        }

        start.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        executor.shutdown()

        assertEquals(services.map { it.tag }.toSet(), registry.tags.toSet())
        assertEquals(services.size, registry.tags.size)
    }
}
