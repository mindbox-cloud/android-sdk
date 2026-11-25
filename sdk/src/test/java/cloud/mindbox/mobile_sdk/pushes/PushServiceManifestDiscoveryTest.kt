package cloud.mindbox.mobile_sdk.pushes

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.getCurrentProcessName
import cloud.mindbox.mobile_sdk.isMainProcess
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal object ManifestFirebaseService : MindboxPushService {
    override val tag: String = "FCM"

    var handler: PushServiceHandler = handlerOf("FCM")

    override fun getServiceHandler(logger: MindboxLogger, exceptionHandler: ExceptionHandler) = handler
}

internal object ManifestHuaweiService : MindboxPushService {
    override val tag: String = "HCM"

    var handler: PushServiceHandler = handlerOf("HCM")

    override fun getServiceHandler(logger: MindboxLogger, exceptionHandler: ExceptionHandler) = handler
}

internal object NotAPushService

private fun handlerOf(tag: String): PushServiceHandler = mockk {
    every { notificationProvider } returns tag
    every { isServiceAvailable(any()) } returns true
    coEvery { initService(any()) } just runs
}

class PushServiceManifestDiscoveryTest {

    private val registry = PushServiceRegistry { CoroutineScope(SupervisorJob() + Dispatchers.Unconfined) }

    private val firebaseMetaData = "cloud.mindbox.push.provider.firebase" to ManifestFirebaseService::class.java.name
    private val huaweiMetaData = "cloud.mindbox.push.provider.huawei" to ManifestHuaweiService::class.java.name

    private fun PushServiceRegistry.tags(context: Context): List<String> =
        resolveHandlers(context).map { it.notificationProvider }

    private val PushServiceRegistry.initializedTags: List<String>
        get() = initializedHandlers.map { it.notificationProvider }

    @Before
    fun setUp() {
        ManifestFirebaseService.handler = handlerOf("FCM")
        ManifestHuaweiService.handler = handlerOf("HCM")
    }

    @After
    fun tearDown() {
        unmockkAll()
        Mindbox.pushServiceRegistry = PushServiceRegistry { Mindbox.mindboxScope }
    }

    @Test
    fun `services from the manifest are used in the SDK order when no list is passed`() {
        val context = contextWithMetaData(huaweiMetaData, firebaseMetaData)

        assertEquals(listOf("FCM", "HCM"), registry.tags(context))
        assertTrue(registry.isRegistered(context, "HCM"))
    }

    @Test
    fun `nothing is known before the manifest is read`() {
        assertEquals(emptyList<PushServiceHandler>(), registry.handlers)
        assertFalse(registry.isSettled)
    }

    @Test
    fun `an empty list is the same as no list, so services come from the manifest`() {
        val context = contextWithMetaData(huaweiMetaData, firebaseMetaData)
        registry.registerExplicit(emptyList())

        assertEquals(listOf("FCM", "HCM"), registry.tags(context))
    }

    @Test
    fun `the manifest is not read when a list was passed`() {
        val packageManager = packageManagerWithMetaData(firebaseMetaData)
        registry.registerExplicit(listOf(ManifestHuaweiService))

        assertEquals(listOf("HCM"), registry.tags(contextWith(packageManager)))
        verify(exactly = 0) { packageManager.getApplicationInfo(any<String>(), any<Int>()) }
    }

    @Test
    fun `a list passed after the manifest was read overrides its services`() {
        val context = contextWithMetaData(firebaseMetaData, huaweiMetaData)
        registry.resolveHandlers(context)

        registry.registerExplicit(listOf(ManifestHuaweiService))

        assertEquals(listOf("HCM"), registry.tags(context))
        assertFalse(registry.isRegistered(context, "FCM"))
    }

    @Test
    fun `an empty list passed after the manifest was read keeps its services`() {
        val context = contextWithMetaData(firebaseMetaData, huaweiMetaData)
        registry.resolveHandlers(context)

        registry.registerExplicit(emptyList())

        assertEquals(listOf("FCM", "HCM"), registry.tags(context))
    }

    @Test
    fun `the manifest is read once`() {
        val packageManager = packageManagerWithMetaData()
        val context = contextWith(packageManager)

        registry.resolveHandlers(context)
        registry.isRegistered(context, "FCM")

        verify(exactly = 1) { packageManager.getApplicationInfo(any<String>(), any<Int>()) }
    }

    @Test
    fun `a missing class or a class that is not a push service is skipped`() {
        val context = contextWithMetaData(
            "cloud.mindbox.push.provider.missing" to "com.example.MissingPushService",
            "cloud.mindbox.push.provider.other" to NotAPushService::class.java.name,
            firebaseMetaData,
        )

        assertEquals(listOf("FCM"), registry.tags(context))
    }

    @Test
    fun `meta-data of other libraries is ignored`() {
        val context = contextWithMetaData("com.example.sdk.provider" to ManifestFirebaseService::class.java.name)

        assertEquals(emptyList<String>(), registry.tags(context))
    }

    @Test
    fun `a failing package manager leaves the services unknown and does not throw`() = runTest {
        val packageManager = mockk<PackageManager> {
            every { getApplicationInfo(any<String>(), any<Int>()) } throws PackageManager.NameNotFoundException()
        }
        val context = contextWith(packageManager)

        assertFalse(registry.initialize(context))

        assertEquals(emptyList<String>(), registry.tags(context))
        assertFalse(registry.isSettled)
    }

    @Test
    fun `a failed manifest read is repeated on the next call`() {
        val applicationInfo = ApplicationInfo().apply { metaData = null }
        val packageManager = mockk<PackageManager> {
            every { getApplicationInfo(any<String>(), any<Int>()) } throws
                PackageManager.NameNotFoundException() andThen applicationInfo
        }
        val context = contextWith(packageManager)

        registry.resolveHandlers(context)
        registry.resolveHandlers(context)
        registry.resolveHandlers(context)

        verify(exactly = 2) { packageManager.getApplicationInfo(any<String>(), any<Int>()) }
    }

    @Test
    fun `init without a list in an app without provider modules asks to refresh the app info once`() = runTest {
        val context = contextWithMetaData()

        assertTrue(registry.initialize(context))
        assertFalse(registry.initialize(context))
    }

    @Test
    fun `an empty list passed to init in an app without provider modules asks to refresh the app info once`() =
        runTest {
            registry.registerExplicit(emptyList())

            assertTrue(registry.initialize(contextWithMetaData()))
            assertFalse(registry.initialize(contextWithMetaData()))
        }

    @Test
    fun `tokens are not requested in a process where init was not called`() = runTest {
        val context = contextWithMetaData(firebaseMetaData)
        Mindbox.pushServiceRegistry = registry
        registry.resolveHandlers(context)

        val tokens = getPushTokens(context, emptyMap())

        assertEquals(emptyMap<String, String>(), tokens)
        coVerify(exactly = 0) { ManifestFirebaseService.handler.initService(any()) }
        coVerify(exactly = 0) { ManifestFirebaseService.handler.registerToken(any(), any()) }
    }

    @Test
    fun `services from the manifest are not started until init asks for it`() = runTest {
        val context = contextWithMetaData(firebaseMetaData, huaweiMetaData)
        registry.resolveHandlers(context)

        assertFalse(registry.initializePending(context))

        assertEquals(emptyList<String>(), registry.initializedTags)
        coVerify(exactly = 0) { ManifestFirebaseService.handler.initService(any()) }
        coVerify(exactly = 0) { ManifestHuaweiService.handler.initService(any()) }
    }

    @Test
    fun `a list passed after a push only starts its own services`() = runTest {
        val context = contextWithMetaData(firebaseMetaData, huaweiMetaData)
        registry.resolveHandlers(context)
        registry.initializePending(context)

        registry.registerExplicit(listOf(ManifestFirebaseService))

        assertTrue(registry.initialize(context))
        assertEquals(listOf("FCM"), registry.initializedTags)
        coVerify(exactly = 0) { ManifestHuaweiService.handler.initService(any()) }
    }

    @Test
    fun `init without a list starts the services from the manifest`() = runTest {
        val context = contextWithMetaData(firebaseMetaData, huaweiMetaData)

        assertTrue(registry.initialize(context))

        assertEquals(listOf("FCM", "HCM"), registry.initializedTags)
        assertTrue(registry.isSettled)
    }

    @Test
    fun `a list passed to init in a secondary process is used there`() {
        val application = mockk<Application>()
        mockkStatic("cloud.mindbox.mobile_sdk.ExtensionsKt")
        every { any<Context>().getCurrentProcessName() } returns "com.example.app:push"
        every { any<Context>().isMainProcess(any()) } returns false
        Mindbox.pushServiceRegistry = registry

        Mindbox.init(application, mockk<MindboxConfiguration>(), listOf(ManifestHuaweiService))

        assertEquals(listOf("HCM"), registry.handlers.map { it.notificationProvider })
    }

    @Test
    fun `a push is shown in a process where init was not called`() {
        val firebaseMessage = Any()
        val mindboxMessage = MindboxRemoteMessage(
            uniqueKey = "push-1",
            title = "title",
            description = "description",
            pushActions = emptyList(),
            pushLink = null,
            imageUrl = null,
            payload = null,
        )
        ManifestFirebaseService.handler = mockk {
            every { notificationProvider } returns "FCM"
            every { convertToRemoteMessage(any()) } answers { mindboxMessage.takeIf { firstArg<Any>() === firebaseMessage } }
        }
        Mindbox.pushServiceRegistry = registry
        mockkObject(PushNotificationManager)
        coEvery {
            PushNotificationManager.handleRemoteMessage(any(), any(), any(), any(), any(), any(), any(), any())
        } returns true

        val isHandled = Mindbox.handleRemoteMessage(
            context = contextWithMetaData(firebaseMetaData),
            message = firebaseMessage,
            channelId = "channel",
            channelName = "Channel",
            pushSmallIcon = 0,
            defaultActivity = Activity::class.java,
        )

        assertTrue(isHandled)
    }
}
