package cloud.mindbox.mobile_sdk

import android.app.Application
import android.content.Context
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.InitData
import cloud.mindbox.mobile_sdk.models.TokenData
import cloud.mindbox.mobile_sdk.models.UpdateData
import cloud.mindbox.mobile_sdk.pushes.PushNotificationManager
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.pushes.PushToken
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID

class MindboxTest {

    private val context = mockk<Context> {
        every { applicationContext } returns mockk<Application>(relaxed = true)
    }

    private val firstProvider = mockk<PushServiceHandler> {
        every { notificationProvider } returns "FCM"
        coEvery { registerToken(any(), any()) } returns "tokenFCM"
    }

    private val secondProvider = mockk<PushServiceHandler> {
        every { notificationProvider } returns "HMS"
        coEvery { registerToken(any(), any()) } returns "tokenHMS"
    }

    private val thirdProvider = mockk<PushServiceHandler> {
        every { notificationProvider } returns "RuStore"
        coEvery { registerToken(any(), any()) } returns "tokenRuStore"
    }

    @Before
    fun setUp() {
        mockkObject(MindboxPreferences)
        mockkObject(PushNotificationManager)
        mockkObject(MindboxEventManager)
        every { MindboxPreferences.pushTokens } returns mapOf(
            "FCM" to "tokenFCM",
            "HMS" to "tokenHMS",
            "RuStore" to "tokenRuStore",
        )
        every { PushNotificationManager.isNotificationsEnabled(any()) } returns true
        every { MindboxPreferences.isNotificationEnabled } returns true
        every { MindboxPreferences.instanceId } returns "instanceId"
        every { MindboxPreferences.deviceUuid } returns "deviceUUID"
        every { MindboxPreferences.infoUpdatedVersion } returns 1

        Mindbox.pushServiceHandlers = listOf(firstProvider, secondProvider, thirdProvider)
    }

    @Test
    fun `updateAppInfo not call appInfoUpdate when no token`() = runTest {
        Mindbox.updateAppInfo(context)

        verify(exactly = 0) { MindboxEventManager.appInfoUpdate(context, any()) }
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

    @Test
    fun `updateAppInfo not call appInfoUpdate when new token provided`() = runTest {
        coEvery { secondProvider.registerToken(any(), any()) } returns "NEW_TOKEN"

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
                        TokenData(token = "NEW_TOKEN", notificationProvider = "HMS"),
                        TokenData(token = "tokenRuStore", notificationProvider = "RuStore"),
                    ),
                )
            )
        }
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
        every { UUID.randomUUID() } returns uuid
        coEvery { firstProvider.getAdsIdentification(context) } returns "adsId"
        coEvery { secondProvider.getAdsIdentification(context) } returns "adsId"
        coEvery { thirdProvider.getAdsIdentification(context) } returns "adsId"
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
    }
}
