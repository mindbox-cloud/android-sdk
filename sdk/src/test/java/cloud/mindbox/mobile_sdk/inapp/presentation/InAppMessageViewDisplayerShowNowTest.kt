package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import android.app.Application
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppActionCallbacks
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFailureTracker
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTypeWrapper
import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppClick
import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppDismiss
import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppShown
import cloud.mindbox.mobile_sdk.inapp.presentation.view.InAppViewHolder
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason
import com.google.gson.Gson
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The invited show: whatever is on screen is dismissed with honest accounting, then the
 * requested in-app is presented right away — past the queue and the active-show lock.
 */
@RunWith(RobolectricTestRunner::class)
internal class InAppMessageViewDisplayerShowNowTest {

    private lateinit var displayer: InAppMessageViewDisplayerImpl
    private lateinit var failureTracker: InAppFailureTracker

    private val noCallbacks = object : InAppActionCallbacks {
        override val onInAppClick = OnInAppClick {}
        override val onInAppShown = OnInAppShown {}
        override val onInAppDismiss = OnInAppDismiss {}
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

    private fun activeHolder(dismiss: OnInAppDismiss): InAppViewHolder<InAppType.ModalWindow> =
        mockk {
            every { isActive } returns true
            every { wrapper } returns InAppTypeWrapper(
                inAppType = InAppStub.getModalWindow().copy(inAppId = "active-id"),
                inAppActionCallbacks = object : InAppActionCallbacks {
                    override val onInAppClick = OnInAppClick {}
                    override val onInAppShown = OnInAppShown {}
                    override val onInAppDismiss = dismiss
                },
                onRenderStart = {},
            )
            every { onClose() } just runs
        }

    private fun setPrivateField(name: String, value: Any?) {
        InAppMessageViewDisplayerImpl::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(displayer, value)
        }
    }

    private fun setCurrentHolder(holder: InAppViewHolder<*>) = setPrivateField("currentHolder", holder)

    /** A live activity whose window is gone: presenting fails observably, in the failure tracker. */
    private fun givenForegroundActivity() {
        setPrivateField(
            "currentActivity",
            mockk<Activity> {
                every { isFinishing } returns false
                every { window } returns null
            }
        )
    }

    /** A live activity with a real root to draw into — the presentation actually runs. */
    private fun givenForegroundActivityWithRoot(): FrameLayout {
        val root = FrameLayout(ApplicationProvider.getApplicationContext<Application>())
        setPrivateField(
            "currentActivity",
            mockk<Activity> {
                every { isFinishing } returns false
                every { window } returns mockk { every { decorView } returns root }
            }
        )
        return root
    }

    @Suppress("UNCHECKED_CAST")
    private fun queuedInApps(): List<InAppTypeWrapper<InAppType>> =
        InAppMessageViewDisplayerImpl::class.java.getDeclaredField("inAppQueue")
            .apply { isAccessible = true }
            .get(displayer) as List<InAppTypeWrapper<InAppType>>

    @Test
    fun `an active show is dismissed with its real dismiss accounting before the new one presents`() {
        givenForegroundActivity()
        val dismiss = mockk<OnInAppDismiss>(relaxUnitFun = true)
        val holder = activeHolder(dismiss)
        setCurrentHolder(holder)

        displayer.showInAppMessageNow(
            inAppType = InAppStub.getModalWindow().copy(inAppId = "tapped-id"),
            inAppActionCallbacks = noCallbacks,
        )

        // The activity has no window, so presenting lands in the failure tracker — the proof the
        // present ran, and ran strictly after the dismissal freed the lock.
        verifyOrder {
            dismiss.onDismiss()
            holder.onClose()
            failureTracker.sendFailure("tapped-id", FailureReason.PRESENTATION_FAILED, any(), any())
        }
    }

    @Test
    fun `the replaced show reaches the registered app callback as dismissed`() {
        givenForegroundActivity()
        val appCallback = mockk<InAppCallback>(relaxUnitFun = true)
        displayer.registerInAppCallback(appCallback)
        val dismiss = mockk<OnInAppDismiss>(relaxUnitFun = true)
        setCurrentHolder(activeHolder(dismiss))

        displayer.showInAppMessageNow(
            inAppType = InAppStub.getModalWindow().copy(inAppId = "tapped-id"),
            inAppActionCallbacks = noCallbacks,
        )

        // The same order the ordinary close keeps: the app hears about the dismissal first,
        // the internal accounting follows.
        verifyOrder {
            appCallback.onInAppDismissed("active-id")
            dismiss.onDismiss()
        }
    }

    @Test
    fun `a paused show gets the same honest dismiss accounting`() {
        givenForegroundActivity()
        val dismiss = mockk<OnInAppDismiss>(relaxUnitFun = true)
        val paused = activeHolder(dismiss)
        setPrivateField("pausedHolder", paused)

        displayer.showInAppMessageNow(
            inAppType = InAppStub.getModalWindow().copy(inAppId = "tapped-id"),
            inAppActionCallbacks = noCallbacks,
        )

        verifyOrder {
            dismiss.onDismiss()
            paused.onClose()
            failureTracker.sendFailure("tapped-id", FailureReason.PRESENTATION_FAILED, any(), any())
        }
    }

    @Test
    fun `with nothing on screen the requested in-app presents immediately`() {
        givenForegroundActivity()
        val dismiss = mockk<OnInAppDismiss>(relaxUnitFun = true)

        displayer.showInAppMessageNow(
            inAppType = InAppStub.getModalWindow().copy(inAppId = "tapped-id"),
            inAppActionCallbacks = noCallbacks,
        )

        verify(exactly = 0) { dismiss.onDismiss() }
        verify { failureTracker.sendFailure("tapped-id", FailureReason.PRESENTATION_FAILED, any(), any()) }
    }

    @Test
    fun `without a foreground activity nothing is closed and the miss reaches the tracker`() {
        val dismiss = mockk<OnInAppDismiss>(relaxUnitFun = true)
        val holder = activeHolder(dismiss)
        setCurrentHolder(holder)

        displayer.showInAppMessageNow(
            inAppType = InAppStub.getModalWindow().copy(inAppId = "tapped-id"),
            inAppActionCallbacks = noCallbacks,
        )

        // Closing what the user may come back to would trade a missed show for a lost one.
        verify(exactly = 0) { dismiss.onDismiss() }
        verify(exactly = 0) { holder.onClose() }
        verify { failureTracker.sendFailure("tapped-id", FailureReason.PRESENTATION_FAILED, any(), any()) }
    }

    @Test
    fun `the requested in-app reaches presentation on a real root`() {
        // The other cases here prove the show only by the failure that follows it. This one gives
        // the activity something to draw into: the requested in-app starts rendering, and the
        // "nowhere to draw" branch is not the reason anything happened.
        givenForegroundActivityWithRoot()
        var rendered = false

        displayer.showInAppMessageNow(
            inAppType = InAppStub.getModalWindow().copy(inAppId = "tapped-id"),
            inAppActionCallbacks = noCallbacks,
            onRenderStart = { rendered = true },
        )

        assertTrue(rendered)
        verify(exactly = 0) {
            failureTracker.sendFailure(any(), any(), match { details -> details.contains("currentRoot is null") }, any())
        }
    }

    @Test
    fun `the requested show empties the queue, and that is the decision`() {
        // `showInAppMessageNow` goes through `closeInApp()`, which drops whatever the pipeline had
        // queued behind the active show. Pinned as a conscious trade-off, not an accident: an
        // in-app surfacing on top of the story the user has just opened would be worse than a
        // queue that was cleared when they asked for something else.
        givenForegroundActivity()
        setCurrentHolder(activeHolder(mockk(relaxUnitFun = true)))
        displayer.tryShowInAppMessage(
            inAppType = InAppStub.getModalWindow().copy(inAppId = "queued-id"),
            inAppActionCallbacks = noCallbacks,
        )
        assertEquals(listOf("queued-id"), queuedInApps().map { it.inAppType.inAppId })

        displayer.showInAppMessageNow(
            inAppType = InAppStub.getModalWindow().copy(inAppId = "tapped-id"),
            inAppActionCallbacks = noCallbacks,
        )

        assertTrue(queuedInApps().isEmpty())
    }

    @Test
    fun `an embedded variant is never presented as an overlay`() {
        givenForegroundActivity()
        displayer.showInAppMessageNow(
            inAppType = InAppStub.getEmbedded().copy(inAppId = "embedded-id"),
            inAppActionCallbacks = noCallbacks,
        )

        verify(exactly = 0) { failureTracker.sendFailure(any(), any(), any(), any()) }
    }
}
