package cloud.mindbox.mobile_sdk.pushes

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
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

    override fun getServiceHandler(logger: MindboxLogger, exceptionHandler: ExceptionHandler) = handlerOf("HCM")
}

internal object NotAPushService

private fun handlerOf(tag: String): PushServiceHandler = mockk { every { notificationProvider } returns tag }

class PushServiceManifestFallbackTest {

    private val registry = PushServiceRegistry { CoroutineScope(SupervisorJob() + Dispatchers.Unconfined) }

    private val firebaseMetaData = "cloud.mindbox.push.provider.firebase" to ManifestFirebaseService::class.java.name
    private val huaweiMetaData = "cloud.mindbox.push.provider.huawei" to ManifestHuaweiService::class.java.name

    private val PushServiceRegistry.tags: List<String>
        get() = handlers.map { it.notificationProvider }

    private fun packageManagerWith(vararg metaData: Pair<String, String>): PackageManager {
        val bundle = mockk<Bundle> {
            every { keySet() } returns metaData.map { it.first }.toSet()
            metaData.forEach { (key, value) -> every { getString(key) } returns value }
        }
        val applicationInfo = ApplicationInfo().apply { this.metaData = bundle }
        return mockk { every { getApplicationInfo(any<String>(), any<Int>()) } returns applicationInfo }
    }

    private fun contextWith(packageManager: PackageManager): Context = mockk {
        every { this@mockk.packageManager } returns packageManager
        every { packageName } returns "com.example.app"
    }

    private fun contextWithMetaData(vararg metaData: Pair<String, String>): Context =
        contextWith(packageManagerWith(*metaData))

    @Before
    fun setUp() {
        ManifestFirebaseService.handler = handlerOf("FCM")
    }

    @After
    fun tearDown() {
        unmockkObject(PushNotificationManager)
        Mindbox.pushServiceRegistry = PushServiceRegistry { Mindbox.mindboxScope }
    }

    @Test
    fun `services from the manifest are used when App Startup did not run`() {
        registry.discoverFromManifestIfNeeded(contextWithMetaData(huaweiMetaData, firebaseMetaData))

        assertEquals(listOf("FCM", "HCM"), registry.tags)
    }

    @Test
    fun `the fallback works after the empty list that Flutter and React Native pass to init`() {
        registry.registerExplicit(emptyList())

        registry.discoverFromManifestIfNeeded(contextWithMetaData(firebaseMetaData))

        assertEquals(listOf("FCM"), registry.tags)
    }

    @Test
    fun `the manifest is not read when App Startup ran, so a removed initializer stays off`() {
        val packageManager = packageManagerWith(firebaseMetaData)
        registry.markAppStartupRan()

        registry.discoverFromManifestIfNeeded(contextWith(packageManager))

        assertEquals(emptyList<String>(), registry.tags)
        verify(exactly = 0) { packageManager.getApplicationInfo(any<String>(), any<Int>()) }
    }

    @Test
    fun `the manifest is not read when an explicit list was passed`() {
        val packageManager = packageManagerWith(firebaseMetaData)
        registry.registerExplicit(listOf(ManifestHuaweiService))

        registry.discoverFromManifestIfNeeded(contextWith(packageManager))

        assertEquals(listOf("HCM"), registry.tags)
        verify(exactly = 0) { packageManager.getApplicationInfo(any<String>(), any<Int>()) }
    }

    @Test
    fun `the manifest is not read when a module registered at startup`() {
        val packageManager = packageManagerWith(firebaseMetaData)
        registry.registerDiscovered(ManifestHuaweiService)

        registry.discoverFromManifestIfNeeded(contextWith(packageManager))

        assertEquals(listOf("HCM"), registry.tags)
        verify(exactly = 0) { packageManager.getApplicationInfo(any<String>(), any<Int>()) }
    }

    @Test
    fun `the manifest is read once`() {
        val packageManager = packageManagerWith()
        val context = contextWith(packageManager)

        registry.discoverFromManifestIfNeeded(context)
        registry.discoverFromManifestIfNeeded(context)

        verify(exactly = 1) { packageManager.getApplicationInfo(any<String>(), any<Int>()) }
    }

    @Test
    fun `a missing class or a class that is not a push service is skipped`() {
        registry.discoverFromManifestIfNeeded(
            contextWithMetaData(
                "cloud.mindbox.push.provider.missing" to "com.example.MissingPushService",
                "cloud.mindbox.push.provider.other" to NotAPushService::class.java.name,
                firebaseMetaData,
            ),
        )

        assertEquals(listOf("FCM"), registry.tags)
    }

    @Test
    fun `meta-data of other libraries is ignored`() {
        registry.discoverFromManifestIfNeeded(
            contextWithMetaData("com.example.sdk.provider" to ManifestFirebaseService::class.java.name),
        )

        assertEquals(emptyList<String>(), registry.tags)
    }

    @Test
    fun `a failing package manager leaves no services and does not throw`() {
        val packageManager = mockk<PackageManager> {
            every { getApplicationInfo(any<String>(), any<Int>()) } throws PackageManager.NameNotFoundException()
        }

        registry.discoverFromManifestIfNeeded(contextWith(packageManager))

        assertEquals(emptyList<String>(), registry.tags)
    }

    @Test
    fun `a push is shown in a process where App Startup did not run and init was not called`() {
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
