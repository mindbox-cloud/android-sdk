package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import android.app.Application
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppActionCallbacks
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFailureTracker
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTypeWrapper
import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppClick
import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppDismiss
import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppNotShown
import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppShown
import cloud.mindbox.mobile_sdk.inapp.presentation.view.InAppViewHolder
import cloud.mindbox.mobile_sdk.models.InAppStub
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The overlay across a background stint: what the paused holder keeps, what the restored one
 * inherits, and what a candidate that never reached the screen leaves behind.
 */
@RunWith(RobolectricTestRunner::class)
internal class InAppMessageViewDisplayerRestoreTest {

    private lateinit var displayer: InAppMessageViewDisplayerImpl
    private lateinit var failureTracker: InAppFailureTracker

    private class CountingCallbacks : InAppActionCallbacks {
        var shown = 0
        var notShown = 0
        override val onInAppClick = OnInAppClick {}
        override val onInAppShown = OnInAppShown { shown++ }
        override val onInAppDismiss = OnInAppDismiss {}
        override val onInAppNotShown = OnInAppNotShown { notShown++ }
    }

    @Before
    fun setUp() {
        mockkObject(MindboxDI)
        failureTracker = mockk(relaxed = true)
        every { MindboxDI.appModule } returns mockk(relaxed = true) {
            every { gson } returns Gson()
            every { inAppFailureTracker } returns failureTracker
        }
        displayer = InAppMessageViewDisplayerImpl(mockk())
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun field(name: String): Any? =
        InAppMessageViewDisplayerImpl::class.java.getDeclaredField(name)
            .apply { isAccessible = true }
            .get(displayer)

    private fun setField(name: String, value: Any?) {
        InAppMessageViewDisplayerImpl::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(displayer, value)
        }
    }

    private fun currentHolder(): InAppViewHolder<*>? = field("currentHolder") as InAppViewHolder<*>?

    @Suppress("UNCHECKED_CAST")
    private fun queue(): List<InAppTypeWrapper<*>> = field("inAppQueue") as List<InAppTypeWrapper<*>>

    private fun activityWithRoot(): Activity {
        val root = FrameLayout(ApplicationProvider.getApplicationContext<Application>())
        return mockk(relaxed = true) {
            every { isFinishing } returns false
            every { window } returns mockk { every { decorView } returns root }
        }
    }

    private fun activityWithoutWindow(): Activity = mockk(relaxed = true) {
        every { isFinishing } returns false
        every { window } returns null
    }

    /** A modal paused in the background: not reusable, so the restore builds a new holder from its wrapper. */
    private fun pausedModal(callbacks: InAppActionCallbacks, inAppId: String = "paused-id"): InAppViewHolder<*> =
        mockk(relaxed = true) {
            every { canReuseOnRestore(any()) } returns false
            every { wrapper } returns InAppTypeWrapper(
                inAppType = InAppStub.getModalWindow().copy(inAppId = inAppId),
                inAppActionCallbacks = callbacks,
                onRenderStart = {},
            )
        }

    @Test
    fun `the restored holder is built from the original wrapper and keeps its callbacks`() {
        // The original show may never have fired (the image was still loading when the app went
        // to the background): the restored overlay must be able to report the first real show.
        val callbacks = CountingCallbacks()
        setField("pausedHolder", pausedModal(callbacks))

        displayer.onResumeCurrentActivity(activityWithRoot(), { true }) {}

        val restored = currentHolder()
        assertNotNull(restored)
        assertSame(callbacks, restored!!.wrapper.inAppActionCallbacks)
        assertNull(field("pausedHolder"))
    }

    @Test
    fun `a paused overlay keeps the screen taken`() {
        assertFalse(displayer.isInAppActive())

        setField("pausedHolder", pausedModal(CountingCallbacks()))

        assertTrue(displayer.isInAppActive())
    }

    @Test
    fun `restoring the paused overlay tells every queued candidate it will not be shown`() {
        // With no activity the candidate waits in the queue; the restored overlay takes the screen,
        // so the queue entry can only hold a slot in the show budgets for nothing.
        val queued = CountingCallbacks()
        displayer.tryShowInAppMessage(InAppStub.getModalWindow().copy(inAppId = "queued-id"), queued, {}, null)
        assertEquals(1, queue().size)
        setField("pausedHolder", pausedModal(CountingCallbacks()))

        displayer.onResumeCurrentActivity(activityWithRoot(), { true }) {}

        assertEquals(1, queued.notShown)
        assertTrue(queue().isEmpty())
        assertNotNull(currentHolder())
    }

    @Test
    fun `a candidate with no root to draw into is closed and does not block the next one`() {
        val first = CountingCallbacks()
        setField("currentActivity", activityWithoutWindow())

        displayer.tryShowInAppMessage(InAppStub.getModalWindow().copy(inAppId = "first-id"), first, {}, null)

        assertEquals(1, first.notShown)
        assertNull(currentHolder())
        assertFalse(displayer.isInAppActive())

        setField("currentActivity", activityWithRoot())
        displayer.tryShowInAppMessage(InAppStub.getModalWindow().copy(inAppId = "second-id"), CountingCallbacks(), {}, null)

        assertEquals("second-id", currentHolder()?.wrapper?.inAppType?.inAppId)
    }
}
