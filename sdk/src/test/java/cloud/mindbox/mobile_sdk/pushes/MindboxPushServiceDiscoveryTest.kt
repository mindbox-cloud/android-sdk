package cloud.mindbox.mobile_sdk.pushes

import android.app.Activity
import android.content.Context
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MindboxPushServiceDiscoveryTest {

    private val context = mockk<Context>()
    private val firebaseMessage = Any()
    private val mindboxMessage = MindboxRemoteMessage(
        uniqueKey = "push-1",
        title = "title",
        description = "description",
        pushActions = emptyList(),
        pushLink = null,
        imageUrl = null,
        payload = null,
    )

    private var isFirebaseAvailable = true

    private val firebaseHandler = mockk<PushServiceHandler> {
        every { notificationProvider } returns "FCM"
        every { convertToRemoteMessage(any()) } answers { mindboxMessage.takeIf { firstArg<Any>() === firebaseMessage } }
        every { isServiceAvailable(any()) } answers { isFirebaseAvailable }
        coEvery { initService(any()) } just runs
        coEvery { registerToken(any(), any()) } returns "tokenFCM"
    }

    private val firebase = object : MindboxPushService {
        override val tag: String = "FCM"

        override fun getServiceHandler(logger: MindboxLogger, exceptionHandler: ExceptionHandler) = firebaseHandler
    }

    @Before
    fun setUp() {
        Mindbox.pushServiceRegistry = PushServiceRegistry { CoroutineScope(SupervisorJob() + Dispatchers.Unconfined) }
        mockkObject(PushNotificationManager)
    }

    @After
    fun tearDown() {
        unmockkObject(PushNotificationManager)
        Mindbox.pushServiceRegistry = PushServiceRegistry { Mindbox.mindboxScope }
    }

    @Test
    fun `a push service registered by its module is in use without a list in init`() {
        MindboxPushServiceDiscovery.register(firebase)

        assertTrue(Mindbox.pushServiceRegistry.isRegistered("FCM"))
    }

    @Test
    fun `a failed registration does not break the app start`() {
        Mindbox.pushServiceRegistry = mockk {
            every { registerDiscovered(any()) } throws IllegalStateException("registry is broken")
        }

        MindboxPushServiceDiscovery.register(firebase)
    }

    @Test
    fun `a push of a discovered service is shown before Mindbox init is called`() {
        MindboxPushServiceDiscovery.register(firebase)
        coEvery {
            PushNotificationManager.handleRemoteMessage(any(), any(), any(), any(), any(), any(), any(), any())
        } returns true

        val isHandled = Mindbox.handleRemoteMessage(
            context = context,
            message = firebaseMessage,
            channelId = "channel",
            channelName = "Channel",
            pushSmallIcon = 0,
            defaultActivity = Activity::class.java,
        )

        assertTrue(isHandled)
        coVerify(exactly = 1) {
            PushNotificationManager.handleRemoteMessage(
                context = context,
                remoteMessage = mindboxMessage,
                channelId = "channel",
                channelName = "Channel",
                pushSmallIcon = 0,
                channelDescription = null,
                activities = null,
                defaultActivity = Activity::class.java,
            )
        }
    }

    @Test
    fun `push token is requested from a service that delivered a token after being unavailable`() = runTest {
        isFirebaseAvailable = false
        MindboxPushServiceDiscovery.register(firebase)
        Mindbox.pushServiceRegistry.initializeAvailable(context)
        Mindbox.pushServiceRegistry.markAvailable("FCM")

        val tokens = getPushTokens(context, emptyMap())

        assertEquals(mapOf("FCM" to "tokenFCM"), tokens)
    }
}
