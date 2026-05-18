package cloud.mindbox.mobile_sdk.managers

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.Mindbox
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean

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
}
