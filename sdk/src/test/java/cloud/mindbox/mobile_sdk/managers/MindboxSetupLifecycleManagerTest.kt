package cloud.mindbox.mobile_sdk.managers

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import android.app.Activity
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.di.modules.AppModule
import cloud.mindbox.mobile_sdk.embedded.EmbeddedBlocksRegistry
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionStatus
import cloud.mindbox.mobile_sdk.inapp.presentation.view.DataCollector
import cloud.mindbox.mobile_sdk.inapp.presentation.view.InAppInsets
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.utils.TimeProvider
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests for [Mindbox.setupLifecycleManager] and [Mindbox.attachLifecycleCallbacks].
 *
 * Both methods are private, so they are invoked via reflection. The observable side-effects
 * (changes to [LifecycleManager.instance] and its [LifecycleManager.callbacks] field) are used
 * to assert correctness.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
internal class MindboxSetupLifecycleManagerTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        LifecycleManager.instance = null
        setFirstInitCall(true)
        unmockkAll()
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun setFirstInitCall(value: Boolean) {
        val field = Mindbox::class.java.getDeclaredField("firstInitCall")
        field.isAccessible = true
        (field.get(Mindbox) as AtomicBoolean).set(value)
    }

    private fun callSetupLifecycleManager(ctx: Context = context) {
        val method = Mindbox::class.java
            .getDeclaredMethod("setupLifecycleManager", Context::class.java)
        method.isAccessible = true
        method.invoke(Mindbox, ctx)
    }

    private fun callAttachLifecycleCallbacks() {
        val method = Mindbox::class.java.getDeclaredMethod("attachLifecycleCallbacks")
        method.isAccessible = true
        method.invoke(Mindbox)
    }

    // ── setupLifecycleManager ─────────────────────────────────────────────────

    @Test
    fun `register is called as fallback when startup initializer did not run`() {
        assertNull(LifecycleManager.instance)

        callSetupLifecycleManager()

        assertNotNull(LifecycleManager.instance)
    }

    @Test
    fun `existing instance is kept when startup initializer already ran`() {
        val existing = LifecycleManager(null, null, isAppInBackground = true)
        LifecycleManager.instance = existing

        callSetupLifecycleManager()

        assertSame(
            "register must not be called when already registered",
            existing,
            LifecycleManager.instance,
        )
    }

    @Test
    fun `scheduleReinitTrackVisit is called when already registered and it is not the first init`() {
        val spy = spyk(LifecycleManager(null, null, isAppInBackground = true))
        LifecycleManager.instance = spy
        setFirstInitCall(false)

        callSetupLifecycleManager()

        verify(exactly = 1) { spy.scheduleReinitTrackVisit() }
    }

    @Test
    fun `scheduleReinitTrackVisit is not called on the first init even when already registered`() {
        val spy = spyk(LifecycleManager(null, null, isAppInBackground = true))
        LifecycleManager.instance = spy
        // firstInitCall is true by default — no override needed

        callSetupLifecycleManager()

        verify(exactly = 0) { spy.scheduleReinitTrackVisit() }
    }

    // ── attachLifecycleCallbacks ──────────────────────────────────────────────

    @Test
    fun `attachLifecycleCallbacks sets callbacks when instance exists`() {
        LifecycleManager.instance = LifecycleManager(null, null, isAppInBackground = true)
        assertNull(LifecycleManager.instance!!.callbacks)

        callAttachLifecycleCallbacks()

        assertNotNull(LifecycleManager.instance!!.callbacks)
    }

    @Test
    fun `attachLifecycleCallbacks is a no-op when instance is null`() {
        assertNull(LifecycleManager.instance)

        callAttachLifecycleCallbacks() // must not throw
    }

    @Test
    fun `attachLifecycleCallbacks replaces callbacks on each call`() {
        val manager = LifecycleManager(null, null, isAppInBackground = true)
        LifecycleManager.instance = manager

        callAttachLifecycleCallbacks()
        val first = manager.callbacks

        callAttachLifecycleCallbacks()
        val second = manager.callbacks

        assertNotNull(first)
        assertNotNull(second)
        assertNotSame("each init call must install a fresh Callbacks instance", first, second)
    }

    @Test
    fun `the app leaving the foreground makes block shows wait for the session check of its return`() {
        val sessionStorageManager = mockk<SessionStorageManager>(relaxed = true)
        val manager = LifecycleManager(null, null, isAppInBackground = false)
        LifecycleManager.instance = manager
        withAppModule(sessionStorageManager = sessionStorageManager) {
            callAttachLifecycleCallbacks()

            manager.callbacks?.onAppMovedToBackground()

            verify(exactly = 1) { sessionStorageManager.onAppLeftForeground() }
        }
    }

    @Test
    fun `a handled return releases what waits for its session check`() {
        val sessionStorageManager = mockk<SessionStorageManager>(relaxed = true)
        val manager = LifecycleManager(null, null, isAppInBackground = true)
        LifecycleManager.instance = manager
        withAppModule(sessionStorageManager = sessionStorageManager) {
            callAttachLifecycleCallbacks()

            manager.callbacks?.onAppReturnHandled()

            verify(exactly = 1) { sessionStorageManager.onReturnChecked() }
        }
    }

    @Test
    fun `a handled return tells the embedded blocks once the session check is marked over`() {
        val sessionStorageManager = mockk<SessionStorageManager>(relaxed = true)
        val blocksRegistry = mockk<EmbeddedBlocksRegistry>(relaxed = true)
        val manager = LifecycleManager(null, null, isAppInBackground = true)
        LifecycleManager.instance = manager
        withAppModule(sessionStorageManager = sessionStorageManager, blocksRegistry = blocksRegistry) {
            callAttachLifecycleCallbacks()

            manager.callbacks?.onAppReturnHandled()

            verifyOrder {
                sessionStorageManager.onReturnChecked()
                blocksRegistry.onReturnCheckOver()
            }
        }
    }

    @Test
    fun `the first screen resumed is passed to the embedded blocks`() {
        val blocksRegistry = mockk<EmbeddedBlocksRegistry>(relaxed = true)
        val manager = LifecycleManager(null, null, isAppInBackground = true)
        LifecycleManager.instance = manager
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        withAppModule(blocksRegistry = blocksRegistry) {
            callAttachLifecycleCallbacks()

            manager.callbacks?.onFirstActivityResumed(activity)

            verify(exactly = 1) { blocksRegistry.onAppResumedOn(activity) }
        }
    }

    @Test
    fun `a page built as soon as a visit starts a new session is told the source of that visit`() {
        val timeProvider = mockk<TimeProvider> { every { currentTimeMillis() } returns 10_000L }
        val sessionStorageManager = SessionStorageManager(timeProvider).apply {
            lastTrackVisitSendTime.set(1_000L)
            state.sessionTime = 1_000L.milliseconds
        }
        val newSessionPages = mutableListOf<JsonObject>()
        sessionStorageManager.addSessionExpirationListener { newSessionPages += pagePayload(sessionStorageManager) }
        mockkObject(DbManager)
        every { DbManager.getConfigurations() } returns null
        val manager = LifecycleManager(null, null, isAppInBackground = false)
        LifecycleManager.instance = manager
        withAppModule(sessionStorageManager = sessionStorageManager) {
            callAttachLifecycleCallbacks()

            withIsolatedEventScope {
                manager.callbacks?.onTrackVisitReady("link", "https://example.com/promo")
            }

            val page = newSessionPages.single()
            assertEquals("link", page.get("trackVisitSource")?.asString)
            assertEquals("https://example.com/promo", page.get("trackVisitRequestUrl")?.asString)
        }
    }

    private fun withIsolatedEventScope(block: () -> Unit) {
        val original = Mindbox.eventScope
        val isolated = CoroutineScope(Job())
        setEventScope(isolated)
        try {
            block()
        } finally {
            isolated.cancel()
            setEventScope(original)
        }
    }

    private fun setEventScope(scope: CoroutineScope) {
        Mindbox::class.java.getDeclaredField("eventScope")
            .apply { isAccessible = true }
            .set(Mindbox, scope)
    }

    private fun pagePayload(sessionStorageManager: SessionStorageManager): JsonObject {
        val permissionManager = mockk<PermissionManager> {
            every { getCameraPermissionStatus() } returns PermissionStatus.DENIED
            every { getLocationPermissionStatus() } returns PermissionStatus.DENIED
            every { getMicrophonePermissionStatus() } returns PermissionStatus.DENIED
            every { getNotificationPermissionStatus() } returns PermissionStatus.DENIED
            every { getPhotoLibraryPermissionStatus() } returns PermissionStatus.DENIED
        }
        val payload = DataCollector(
            appContext = context,
            sessionStorageManager = sessionStorageManager,
            permissionManager = permissionManager,
            configuration = mockk<Configuration>(relaxed = true),
            params = emptyMap(),
            inAppInsets = InAppInsets(),
            gson = Gson(),
            inAppId = "in-app-id",
            operation = null,
        ).get()
        return JsonParser.parseString(payload).asJsonObject
    }

    private fun withAppModule(
        sessionStorageManager: SessionStorageManager = mockk(relaxed = true),
        blocksRegistry: EmbeddedBlocksRegistry? = null,
        block: () -> Unit,
    ) {
        mockkObject(MindboxDI)
        every { MindboxDI.appModule } returns mockk<AppModule>(relaxed = true) {
            every { this@mockk.sessionStorageManager } returns sessionStorageManager
            every { embeddedBlocksRegistryIfCreated } returns blocksRegistry
        }
        block()
    }
}
