package cloud.mindbox.mobile_sdk

import android.app.Activity
import android.app.Application
import android.content.Context
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManager
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.managers.DeliveryPauseManager
import cloud.mindbox.mobile_sdk.managers.DeviceUuidResolver
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.managers.TrackingIdsResolver
import cloud.mindbox.mobile_sdk.models.AsyncEventSendResult
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.Milliseconds
import cloud.mindbox.mobile_sdk.models.Timestamp
import cloud.mindbox.mobile_sdk.models.TrackingId
import cloud.mindbox.mobile_sdk.models.InitData
import cloud.mindbox.mobile_sdk.models.TokenData
import cloud.mindbox.mobile_sdk.models.UpdateData
import cloud.mindbox.mobile_sdk.pushes.DeliveredUrl
import cloud.mindbox.mobile_sdk.pushes.MindboxPushService
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.pushes.PrefPushToken
import cloud.mindbox.mobile_sdk.pushes.PushNotificationManager
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.pushes.PushServiceRegistry
import cloud.mindbox.mobile_sdk.pushes.PushToken
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager
import cloud.mindbox.mobile_sdk.services.PushDeliveryWorkManager
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import cloud.mindbox.mobile_sdk.utils.mockLogger
import cloud.mindbox.mobile_sdk.utils.setMindboxScope
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class MindboxTest {

    private companion object {
        const val RECEIVED_AT = 1_795_000_000_000L
    }

    private val context = mockk<Context> {
        every { applicationContext } returns mockk<Application>(relaxed = true)
    }

    private val firstProvider = mockk<PushServiceHandler> {
        every { notificationProvider } returns "FCM"
        coEvery { registerToken(any(), any()) } returns "tokenFCM"
        every { isServiceAvailable(any()) } returns true
        coEvery { initService(any()) } just runs
    }

    private val secondProvider = mockk<PushServiceHandler> {
        every { notificationProvider } returns "HMS"
        coEvery { registerToken(any(), any()) } returns "tokenHMS"
        every { isServiceAvailable(any()) } returns true
        coEvery { initService(any()) } just runs
    }

    private val trackingIdsResolver = mockk<TrackingIdsResolver>()
    private val deviceUuidResolver = mockk<DeviceUuidResolver> {
        coEvery { resolve(any()) } returns "deviceUUID"
    }

    private val thirdProvider = mockk<PushServiceHandler> {
        every { notificationProvider } returns "RuStore"
        coEvery { registerToken(any(), any()) } returns "tokenRuStore"
        every { isServiceAvailable(any()) } returns true
        coEvery { initService(any()) } just runs
    }

    private lateinit var originalScope: CoroutineScope

    @Before
    fun setUp() {
        originalScope = Mindbox.mindboxScope
        setMindboxScope(CoroutineScope(Dispatchers.Unconfined))
        mockkObject(MindboxDI)
        coEvery { trackingIdsResolver.resolve(any()) } returns null
        every { trackingIdsResolver.hasChanged(any()) } returns false
        every { trackingIdsResolver.markSent(any()) } just runs
        every { trackingIdsResolver.resetSent() } just runs
        every { MindboxDI.appModule } returns mockk(relaxed = true) {
            every { trackingIdsResolver } returns this@MindboxTest.trackingIdsResolver
            every { deviceUuidResolver } returns this@MindboxTest.deviceUuidResolver
        }
        mockkObject(MindboxPreferences)
        mockkObject(PushNotificationManager)
        mockkObject(MindboxEventManager)
        every { MindboxPreferences.pushTokens } returns mapOf(
            "FCM" to PrefPushToken("tokenFCM", 1L),
            "HMS" to PrefPushToken("tokenHMS", 2L),
            "RuStore" to PrefPushToken("tokenRuStore", 3L),
        )
        every { PushNotificationManager.isNotificationsEnabled(any()) } returns true
        every { MindboxPreferences.isNotificationEnabled } returns true
        every { MindboxPreferences.instanceId } returns "instanceId"
        every { MindboxPreferences.deviceUuid } returns "deviceUUID"
        every { MindboxPreferences.firstInitializationTime } returns null
        every { MindboxPreferences.firstInitializationTime = any() } just runs
        every { MindboxPreferences.infoUpdatedVersion } returns 1

        Mindbox.pushServiceRegistry = PushServiceRegistry { CoroutineScope(SupervisorJob() + Dispatchers.Unconfined) }
            .apply {
                registerExplicit(listOf(firstProvider, secondProvider, thirdProvider).map(::pushService))
                runBlocking { initialize(context) }
            }
    }

    @After
    fun tearDown() {
        setMindboxScope(originalScope)
        Mindbox.pushServiceRegistry = PushServiceRegistry { Mindbox.mindboxScope }
        unmockkAll()
    }

    private fun pushService(handler: PushServiceHandler): MindboxPushService = object : MindboxPushService {
        override val tag: String = handler.notificationProvider

        override fun getServiceHandler(logger: MindboxLogger, exceptionHandler: ExceptionHandler) = handler
    }

    @Test
    fun `updateAppInfo not call appInfoUpdate when no token`() = runTest {
        Mindbox.updateAppInfo(context)

        verify(exactly = 0) { MindboxEventManager.appInfoUpdate(context, any()) }
    }

    private val googleIds = listOf(TrackingId("google", "38400000-8cf0-11bd-b23e-10b96e40000d"))

    private fun updateData(trackingIds: List<TrackingId>?) = UpdateData(
        isNotificationsEnabled = true,
        instanceId = "instanceId",
        version = 1,
        tokens = listOf(
            TokenData(token = "tokenFCM", notificationProvider = "FCM"),
            TokenData(token = "tokenHMS", notificationProvider = "HMS"),
            TokenData(token = "tokenRuStore", notificationProvider = "RuStore"),
        ),
        trackingIds = trackingIds,
    )

    @Test
    fun `updateAppInfo sends changed tracking ids in a tracking event of their own under a single version`() = runTest {
        every { MindboxPreferences.infoUpdatedVersion } returnsMany listOf(1, 2)
        coEvery { trackingIdsResolver.resolve(any()) } returns googleIds
        every { trackingIdsResolver.hasChanged(googleIds) } returns true

        Mindbox.updateAppInfo(context, PushToken("FCM", "tokenFCM"))

        verify(exactly = 1) { MindboxEventManager.appTrackingIdsUpdate(context, updateData(googleIds)) }
        verify(exactly = 0) { MindboxEventManager.appInfoUpdate(any(), any(), any()) }
        verify(exactly = 1) { trackingIdsResolver.markSent(googleIds) }
        verify(exactly = 1) { MindboxPreferences.infoUpdatedVersion }
    }

    @Test
    fun `updateAppInfo sends nothing when only unchanged tracking ids were read`() = runTest {
        coEvery { trackingIdsResolver.resolve(any()) } returns googleIds
        every { trackingIdsResolver.hasChanged(googleIds) } returns false

        Mindbox.updateAppInfo(context, PushToken("FCM", "tokenFCM"))

        verify(exactly = 0) { MindboxEventManager.appInfoUpdate(context, any()) }
        verify(exactly = 0) { MindboxEventManager.appTrackingIdsUpdate(any(), any()) }
        verify(exactly = 0) { trackingIdsResolver.markSent(any()) }
    }

    @Test
    fun `updateAppInfo leaves the ids field out when there is no tracking host`() = runTest {
        coEvery { trackingIdsResolver.resolve(any()) } returns null
        coEvery { firstProvider.registerToken(context, any()) } returns "NEW_TOKEN"

        Mindbox.updateAppInfo(context, PushToken("FCM", "NEW_TOKEN"))

        verify(exactly = 1) { MindboxEventManager.appInfoUpdate(context, match { it.trackingIds == null }) }
        verify(exactly = 0) { MindboxEventManager.appTrackingIdsUpdate(any(), any()) }
        verify(exactly = 0) { trackingIdsResolver.markSent(any()) }
    }

    @Test
    fun `updateAppInfo erases the tracking ids with an empty array in the regular operation`() = runTest {
        coEvery { trackingIdsResolver.resolve(any()) } returns emptyList()
        every { trackingIdsResolver.hasChanged(emptyList()) } returns true

        Mindbox.updateAppInfo(context, PushToken("FCM", "tokenFCM"))

        verify(exactly = 1) { MindboxEventManager.appInfoUpdate(context, updateData(emptyList()), refreshesKeepalive = false) }
        verify(exactly = 0) { MindboxEventManager.appTrackingIdsUpdate(any(), any()) }
        verify(exactly = 1) { trackingIdsResolver.markSent(emptyList()) }
    }

    @Test
    fun `updateAppInfo erases the tracking ids together with a token change in one operation`() = runTest {
        coEvery { firstProvider.registerToken(context, any()) } returns "NEW_TOKEN"
        coEvery { trackingIdsResolver.resolve(any()) } returns emptyList()
        every { trackingIdsResolver.hasChanged(emptyList()) } returns true

        Mindbox.updateAppInfo(context, PushToken("FCM", "NEW_TOKEN"))

        verify(exactly = 1) {
            MindboxEventManager.appInfoUpdate(
                context,
                match { it.tokens.first().token == "NEW_TOKEN" && it.trackingIds == emptyList<TrackingId>() },
                refreshesKeepalive = true,
            )
        }
    }

    @Test
    fun `updateAppInfo sends a token change and new tracking ids as two operations`() = runTest {
        coEvery { firstProvider.registerToken(context, any()) } returns "NEW_TOKEN"
        coEvery { trackingIdsResolver.resolve(any()) } returns googleIds
        every { trackingIdsResolver.hasChanged(googleIds) } returns true

        Mindbox.updateAppInfo(context, PushToken("FCM", "NEW_TOKEN"))

        verify(exactly = 1) {
            MindboxEventManager.appInfoUpdate(
                context,
                match { it.tokens.first().token == "NEW_TOKEN" && it.trackingIds == null },
                refreshesKeepalive = true,
            )
        }
        verify(exactly = 1) {
            MindboxEventManager.appTrackingIdsUpdate(
                context,
                match { it.tokens.first().token == "NEW_TOKEN" && it.trackingIds == googleIds },
            )
        }
    }

    @Test
    fun `updateAppInfo gives the tracking event a version of its own next to a token change`() = runTest {
        every { MindboxPreferences.infoUpdatedVersion } returnsMany listOf(1, 2)
        coEvery { firstProvider.registerToken(context, any()) } returns "NEW_TOKEN"
        coEvery { trackingIdsResolver.resolve(any()) } returns googleIds
        every { trackingIdsResolver.hasChanged(googleIds) } returns true

        Mindbox.updateAppInfo(context, PushToken("FCM", "NEW_TOKEN"))

        verify(exactly = 1) { MindboxEventManager.appInfoUpdate(context, match { it.version == 1 }, refreshesKeepalive = true) }
        verify(exactly = 1) { MindboxEventManager.appTrackingIdsUpdate(context, match { it.version == 2 }) }
    }

    @Test
    fun `updateAppInfo sends a token change without the unchanged tracking ids`() = runTest {
        coEvery { firstProvider.registerToken(context, any()) } returns "NEW_TOKEN"
        coEvery { trackingIdsResolver.resolve(any()) } returns googleIds
        every { trackingIdsResolver.hasChanged(googleIds) } returns false

        Mindbox.updateAppInfo(context, PushToken("FCM", "NEW_TOKEN"))

        verify(exactly = 1) {
            MindboxEventManager.appInfoUpdate(
                context,
                match { it.tokens.first().token == "NEW_TOKEN" && it.trackingIds == null }
            )
        }
        verify(exactly = 0) { trackingIdsResolver.markSent(any()) }
    }

    @Test
    fun `firstInitialization sends the install before it reads the tracking ids`() = runTest {
        coEvery { trackingIdsResolver.resolve(any()) } returns googleIds
        every { trackingIdsResolver.hasChanged(googleIds) } returns true

        Mindbox.firstInitialization(context, mockk(relaxed = true))

        coVerifyOrder {
            MindboxEventManager.appInstalled(context, any(), any())
            trackingIdsResolver.resetSent()
            trackingIdsResolver.resolve(any())
        }
    }

    @Test
    fun `firstInitialization sends the tracking ids in a follow-up tracking event`() = runTest {
        coEvery { trackingIdsResolver.resolve(any()) } returns googleIds
        every { trackingIdsResolver.hasChanged(googleIds) } returns true

        Mindbox.firstInitialization(context, mockk(relaxed = true))

        verify(exactly = 1) { MindboxEventManager.appTrackingIdsUpdate(context, match { it.trackingIds == googleIds }) }
        verify(exactly = 0) { MindboxEventManager.appInfoUpdate(any(), any(), any()) }
        verify(exactly = 1) { trackingIdsResolver.markSent(googleIds) }
    }

    @Test
    fun `firstInitialization sends no ApplicationInfoUpdated when there are no tracking ids`() = runTest {
        coEvery { trackingIdsResolver.resolve(any()) } returns emptyList()
        every { trackingIdsResolver.hasChanged(emptyList()) } returns false

        Mindbox.firstInitialization(context, mockk(relaxed = true))

        verify(exactly = 1) { MindboxEventManager.appInstalled(context, any(), any()) }
        verify(exactly = 0) { MindboxEventManager.appInfoUpdate(any(), any()) }
        verify(exactly = 0) { MindboxEventManager.appTrackingIdsUpdate(any(), any()) }
        verify(exactly = 0) { trackingIdsResolver.markSent(any()) }
    }

    @Test
    fun `firstInitialization sends no ApplicationInfoUpdated when the tracking ids could not be read`() = runTest {
        coEvery { trackingIdsResolver.resolve(any()) } returns null

        Mindbox.firstInitialization(context, mockk(relaxed = true))

        verify(exactly = 1) { MindboxEventManager.appInstalled(context, any(), any()) }
        verify(exactly = 0) { MindboxEventManager.appInfoUpdate(any(), any()) }
        verify(exactly = 0) { MindboxEventManager.appTrackingIdsUpdate(any(), any()) }
        verify(exactly = 0) { trackingIdsResolver.markSent(any()) }
    }

    @Test
    fun `updateAppInfo not call appInfoUpdate when token not changed`() = runTest {
        Mindbox.updateAppInfo(context, PushToken("FCM", "tokenFCM"))

        verify(exactly = 0) { MindboxEventManager.appInfoUpdate(context, any()) }
    }

    @Test
    fun `updateAppInfo not call appInfoUpdate when tokens not provided`() = runTest {
        coEvery { firstProvider.registerToken(context, any()) } returns null
        coEvery { secondProvider.registerToken(context, any()) } returns null
        coEvery { thirdProvider.registerToken(context, any()) } returns null

        Mindbox.updateAppInfo(context)

        verify(exactly = 0) { MindboxEventManager.appInfoUpdate(context, any()) }
    }

    @Test
    fun `updateAppInfo call appInfoUpdate when tokens not saved`() = runTest {
        every { MindboxPreferences.pushTokens } returns mapOf()

        Mindbox.updateAppInfo(context)

        verify(exactly = 1) {
            MindboxEventManager.appInfoUpdate(
                context,
                UpdateData(
                    isNotificationsEnabled = true,
                    instanceId = "instanceId",
                    version = 1,
                    tokens = listOf(
                        TokenData(token = "tokenFCM", notificationProvider = "FCM"),
                        TokenData(token = "tokenHMS", notificationProvider = "HMS"),
                        TokenData(token = "tokenRuStore", notificationProvider = "RuStore"),
                    ),
                )
            )
        }
    }

    @Test
    fun `updateAppInfo call appInfoUpdate when token changed`() = runTest {
        coEvery { firstProvider.registerToken(context, any()) } returns "NEW_TOKEN"

        Mindbox.updateAppInfo(context, PushToken("FCM", "NEW_TOKEN"))

        verify(exactly = 1) {
            MindboxEventManager.appInfoUpdate(
                context,
                UpdateData(
                    isNotificationsEnabled = true,
                    instanceId = "instanceId",
                    version = 1,
                    tokens = listOf(
                        TokenData(token = "NEW_TOKEN", notificationProvider = "FCM"),
                        TokenData(token = "tokenHMS", notificationProvider = "HMS"),
                        TokenData(token = "tokenRuStore", notificationProvider = "RuStore"),
                    ),
                )
            )
        }
    }

    private fun registryWithoutInit(vararg handlers: PushServiceHandler): PushServiceRegistry {
        clearMocks(firstProvider, secondProvider, thirdProvider, answers = false)
        mockkObject(Mindbox)
        every { Mindbox.initComponents(any()) } just runs
        every { MindboxPreferences.isFirstInitialize } returns false
        return PushServiceRegistry { CoroutineScope(SupervisorJob() + Dispatchers.Unconfined) }
            .apply { registerExplicit(handlers.map(::pushService)) }
    }

    @Test
    fun `updatePushToken without init starts only its provider and sends the token the provider returns`() {
        coEvery { firstProvider.registerToken(context, any()) } returns "FETCHED_TOKEN"
        Mindbox.pushServiceRegistry = registryWithoutInit(firstProvider, secondProvider, thirdProvider)

        Mindbox.updatePushToken(context, "CALLBACK_TOKEN", pushService(firstProvider))

        coVerify(exactly = 1) { firstProvider.initService(any()) }
        coVerify(exactly = 0) { secondProvider.initService(any()) }
        coVerify(exactly = 0) { thirdProvider.initService(any()) }
        verify(exactly = 1) {
            MindboxEventManager.appInfoUpdate(
                context,
                UpdateData(
                    isNotificationsEnabled = true,
                    instanceId = "instanceId",
                    version = 1,
                    tokens = listOf(
                        TokenData(token = "FETCHED_TOKEN", notificationProvider = "FCM"),
                        TokenData(token = "tokenHMS", notificationProvider = "HMS"),
                        TokenData(token = "tokenRuStore", notificationProvider = "RuStore"),
                    ),
                )
            )
        }
    }

    @Test
    fun `updatePushToken without init ignores a provider left out of the list`() {
        Mindbox.pushServiceRegistry = registryWithoutInit(secondProvider, thirdProvider)

        Mindbox.updatePushToken(context, "CALLBACK_TOKEN", pushService(firstProvider))

        coVerify(exactly = 0) { firstProvider.initService(any()) }
        verify(exactly = 0) { MindboxEventManager.appInfoUpdate(any(), any()) }
    }

    @Test
    fun `updateAppInfo not call appInfoUpdate when null token provided`() = runTest {
        coEvery { thirdProvider.registerToken(any(), any()) } returns null

        Mindbox.updateAppInfo(context)

        verify(exactly = 0) { MindboxEventManager.appInfoUpdate(context, any()) }
    }

    @Test
    fun `updateAppInfo call appInfoUpdate when null and new tokens provided`() = runTest {
        coEvery { thirdProvider.registerToken(any(), any()) } returns null
        coEvery { secondProvider.registerToken(any(), any()) } returns "NEW_TOKEN"

        Mindbox.updateAppInfo(context)

        verify(exactly = 1) {
            MindboxEventManager.appInfoUpdate(
                context, UpdateData(
                    isNotificationsEnabled = true,
                    instanceId = "instanceId",
                    version = 1,
                    tokens = listOf(
                        TokenData(token = "tokenFCM", notificationProvider = "FCM"),
                        TokenData(token = "NEW_TOKEN", notificationProvider = "HMS"),
                        TokenData(token = "tokenRuStore", notificationProvider = "RuStore"),
                    ),
                )
            )
        }
    }

    @Test
    fun `updateAppInfo call appInfoUpdate when token provided`() = runTest {
        every { MindboxPreferences.pushTokens } returns mapOf()

        Mindbox.updateAppInfo(context, PushToken("HMS", "tokenHMS"))

        verify(exactly = 1) {
            MindboxEventManager.appInfoUpdate(
                context, UpdateData(
                    isNotificationsEnabled = true,
                    instanceId = "instanceId",
                    version = 1,
                    tokens = listOf(
                        TokenData(token = "tokenFCM", notificationProvider = "FCM"),
                        TokenData(token = "tokenHMS", notificationProvider = "HMS"),
                        TokenData(token = "tokenRuStore", notificationProvider = "RuStore"),
                    ),
                )
            )
        }
    }

    @Test
    fun `firstInitialization call appInfoUpdate`() = runTest {
        val uuid = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")
        mockkStatic(UUID::class)
        try {
            every { UUID.randomUUID() } returns uuid
            every { MindboxPreferences.pushTokens } returns mapOf()

            Mindbox.firstInitialization(context, mockk(relaxed = true))

            verify(exactly = 1) {
                MindboxEventManager.appInstalled(
                    context,
                    InitData(
                        isNotificationsEnabled = true,
                        externalDeviceUUID = "",
                        instanceId = uuid.toString(),
                        version = 0,
                        subscribe = false,
                        installationId = "",
                        ianaTimeZone = null,
                        tokens = listOf(
                            TokenData(token = "tokenFCM", notificationProvider = "FCM"),
                            TokenData(token = "tokenHMS", notificationProvider = "HMS"),
                            TokenData(token = "tokenRuStore", notificationProvider = "RuStore"),
                        ),
                    ),
                    any()
                )
            }
        } finally {
            unmockkStatic(UUID::class)
        }
    }

    @Test
    fun `firstInitialization takes the deviceUuid from the resolver`() = runTest {
        coEvery { deviceUuidResolver.resolve(context) } returns "resolved-uuid"
        every { MindboxPreferences.pushTokens } returns mapOf()

        Mindbox.firstInitialization(context, mockk(relaxed = true))

        verify(exactly = 1) { MindboxEventManager.appInstalled(context, any(), any()) }
        coVerify(exactly = 1) { deviceUuidResolver.resolve(context) }
    }

    @Test
    fun `firstInitialization does not override saved first initialization time`() = runTest {
        every { MindboxPreferences.firstInitializationTime } returns "2025-01-10T07:40:00Z"

        Mindbox.firstInitialization(context, mockk(relaxed = true))

        verify(exactly = 0) {
            MindboxPreferences.firstInitializationTime = any()
        }
    }

    @Test
    fun `firstInitializationDateTime saved when first initialization time`() = runTest {
        every { MindboxPreferences.firstInitializationTime } returns null

        Mindbox.firstInitialization(context, mockk(relaxed = true))

        verify(exactly = 1) {
            MindboxPreferences.firstInitializationTime = any()
        }
    }

    @Test
    fun `getPushTokensSaveDate returns correctly map`() {
        val tokensDate = Mindbox.getPushTokensSaveDate()
        assertEquals(
            mapOf(
                "FCM" to 1L,
                "HMS" to 2L,
                "RuStore" to 3L,
            ), tokensDate
        )
    }

    @Test
    @Suppress("DEPRECATION_ERROR")
    fun `getPushTokenSaveDate returns correctly map`() {
        every { MindboxPreferences.pushTokens } returns mapOf(
            "TEST" to PrefPushToken("tokenRuStore", 1736502001L)
        )

        val timestamp: Long? = SimpleDateFormat(
            "EEE MMM dd HH:mm:ss zzz yyyy",
            Locale.ENGLISH
        ).parse(Mindbox.getPushTokenSaveDate())?.time

        assertEquals(1736502000L, timestamp)
    }

    @Test
    fun `handled push with a delivered url queues its delivery confirmation`() = runTest {
        stubPushReceipt()

        assertTrue(handlePush(deliveredUrl = "https://mbx.ru/d/key/hash?sig=1"))

        verify(exactly = 1) {
            MindboxEventManager.pushDelivered(
                context = any(),
                deliveredUrl = checkNotNull(DeliveredUrl.of("https://mbx.ru/d/key/hash?sig=1")),
                provider = null,
                receivedAt = Timestamp(RECEIVED_AT),
            )
        }
    }

    @Test
    fun `handled push queues its confirmation with its provider`() = runTest {
        stubPushReceipt()

        assertTrue(handlePush(deliveredUrl = "https://mbx.ru/d", provider = "HCM"))

        verify(exactly = 1) {
            MindboxEventManager.pushDelivered(any(), any(), provider = "HCM", receivedAt = Timestamp(RECEIVED_AT))
        }
    }

    @Test
    fun `push converted by a push service is handled with the provider of that service`() {
        val providerMessage = Any()
        val mindboxMessage = remoteMessage()
        every { firstProvider.convertToRemoteMessage(any()) } returns null
        every { secondProvider.convertToRemoteMessage(providerMessage) } returns mindboxMessage
        coEvery { PushNotificationManager.handleRemoteMessage(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns true

        assertTrue(handleMessage(providerMessage))

        coVerify(exactly = 1) {
            PushNotificationManager.handleRemoteMessage(context, mindboxMessage, "HMS", any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `push passed as a ready Mindbox message is handled without a provider`() {
        val mindboxMessage = remoteMessage()
        coEvery { PushNotificationManager.handleRemoteMessage(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns true

        assertTrue(handleMessage(mindboxMessage))

        coVerify(exactly = 1) {
            PushNotificationManager.handleRemoteMessage(context, mindboxMessage, null, any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `handled push without an https delivered url queues no confirmation`() = runTest {
        stubPushReceipt()

        assertTrue(handlePush(deliveredUrl = null))
        assertTrue(handlePush(deliveredUrl = "http://mbx.ru/d/key/hash"))

        verify(exactly = 0) { MindboxEventManager.pushDelivered(any(), any(), any(), any()) }
    }

    @Test
    fun `public onPushReceived queues no delivery confirmation`() {
        stubPushReceipt()

        Mindbox.onPushReceived(context, "push-key")

        verify(exactly = 1) { Mindbox.initComponents(context) }
        verify(exactly = 0) { MindboxEventManager.pushDelivered(any(), any(), any(), any()) }
    }

    private fun stubPushReceipt() {
        mockLogger()
        mockkObject(Mindbox)
        every { Mindbox.initComponents(any()) } just runs
        every { MindboxPreferences.isFirstInitialize } returns true
        every { MindboxEventManager.pushDelivered(any(), any(), any(), any()) } just runs
        val appModule = MindboxDI.appModule
        every { appModule.timeProvider } returns mockk { every { currentTimestamp() } returns Timestamp(RECEIVED_AT) }
        coEvery {
            PushNotificationManager.tryNotifyRemoteMessage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } just runs
    }

    private fun remoteMessage(deliveredUrl: String? = null) = MindboxRemoteMessage(
        uniqueKey = "push-key",
        title = "Title",
        description = "Description",
        pushActions = emptyList(),
        pushLink = null,
        imageUrl = null,
        payload = null,
        deliveredUrl = deliveredUrl,
    )

    private fun handleMessage(message: Any): Boolean = Mindbox.handleRemoteMessage(
        context = context,
        message = message,
        channelId = "channel",
        channelName = "Channel",
        pushSmallIcon = 0,
        defaultActivity = Activity::class.java,
    )

    private suspend fun handlePush(
        deliveredUrl: String?,
        provider: String? = null,
    ): Boolean = PushNotificationManager.handleRemoteMessage(
        context = context,
        remoteMessage = remoteMessage(deliveredUrl),
        provider = provider,
        channelId = "channel",
        channelName = "Channel",
        pushSmallIcon = 0,
        channelDescription = null,
        activities = null,
        defaultActivity = Activity::class.java,
    )

    @Test
    fun `a soft re-init that abandons the awaited config download requests the config once`() {
        val repository = mockk<MobileConfigRepository> { every { abandonAwaitedDownload() } returns true }
        val inAppManager = mockk<InAppMessageManager>(relaxed = true)

        Mindbox.restartAwaitedConfigDownload(repository) { inAppManager }

        verify(exactly = 1) { inAppManager.requestConfig() }
    }

    @Test
    fun `a soft re-init requests no config when no download is awaited`() {
        val repository = mockk<MobileConfigRepository> { every { abandonAwaitedDownload() } returns false }

        Mindbox.restartAwaitedConfigDownload(repository) { error("resolved without an abandoned download") }
    }

    @Test
    fun `a soft re-init before the config repository exists requests no config`() {
        Mindbox.restartAwaitedConfigDownload(null) { error("resolved without an abandoned download") }
    }

    @Test
    fun `a soft re-init requests the abandoned config once after the repository listens again`() {
        val repository = mockk<MobileConfigRepository>(relaxed = true) { every { abandonAwaitedDownload() } returns true }
        val inAppManager = mockk<InAppMessageManager>(relaxed = true)
        val appModule = MindboxDI.appModule
        every { appModule.mobileConfigRepositoryIfCreated } returns repository
        every { appModule.inAppMessageManager } returns inAppManager
        every { MindboxPreferences.resetAppInfoUpdated() } just runs
        mockkObject(DbManager, BackgroundWorkManager)
        val originalScope = Mindbox.mindboxScope
        try {
            every { DbManager.removeAllEventsFromQueue() } just runs
            every { BackgroundWorkManager.cancelAllWork(any()) } just runs
            setMindboxScope(CoroutineScope(Dispatchers.Unconfined))

            Mindbox.softReinitialization(context)

            verify(exactly = 1) { inAppManager.requestConfig() }
            verifyOrder {
                repository.startListening()
                inAppManager.requestConfig()
            }
        } finally {
            setMindboxScope(originalScope)
            unmockkObject(DbManager, BackgroundWorkManager)
        }
    }

    @Test
    fun `a soft re-init lifts delivery pauses`() {
        val deliveryPauseManager = DeliveryPauseManager { Milliseconds(0L) }
        deliveryPauseManager.register(EventType.TrackVisit, AsyncEventSendResult.THROTTLED, deliveryPauseManager.generation)
        val appModule = MindboxDI.appModule
        every { appModule.deliveryPauseManager } returns deliveryPauseManager
        every { MindboxPreferences.resetAppInfoUpdated() } just runs
        mockkObject(DbManager, BackgroundWorkManager)
        val originalScope = Mindbox.mindboxScope
        try {
            every { DbManager.removeAllEventsFromQueue() } just runs
            every { BackgroundWorkManager.cancelAllWork(any()) } just runs
            setMindboxScope(CoroutineScope(Dispatchers.Unconfined))

            Mindbox.softReinitialization(context)

            assertNull(deliveryPauseManager.remainingPause(EventType.TrackVisit))
        } finally {
            setMindboxScope(originalScope)
            unmockkObject(DbManager, BackgroundWorkManager)
        }
    }

    @Test
    fun `a soft re-init clears only the operations queue and keeps push delivery confirmations with their tasks`() {
        every { MindboxPreferences.resetAppInfoUpdated() } just runs
        mockkObject(DbManager, BackgroundWorkManager, PushDeliveryWorkManager)
        val originalScope = Mindbox.mindboxScope
        try {
            every { DbManager.removeAllEventsFromQueue() } just runs
            every { BackgroundWorkManager.cancelAllWork(any()) } just runs
            setMindboxScope(CoroutineScope(Dispatchers.Unconfined))

            Mindbox.softReinitialization(context)

            verify(exactly = 1) { DbManager.removeAllEventsFromQueue() }
            confirmVerified(DbManager)
            verify { PushDeliveryWorkManager wasNot Called }
        } finally {
            setMindboxScope(originalScope)
            unmockkObject(DbManager, BackgroundWorkManager, PushDeliveryWorkManager)
        }
    }
}
