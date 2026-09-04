package cloud.mindbox.mobile_sdk.inapp.presentation

import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppTypeWrapper
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppActionCallbacks
import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppClick
import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppDismiss
import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppNotShown
import cloud.mindbox.mobile_sdk.inapp.domain.models.OnInAppShown
import cloud.mindbox.mobile_sdk.models.InAppStub
import org.junit.Assert.assertEquals
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppFailureTracker
import cloud.mindbox.mobile_sdk.inapp.presentation.callbacks.ComposableInAppCallback
import cloud.mindbox.mobile_sdk.inapp.presentation.view.InAppViewHolder
import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class InAppMessageViewDisplayerImplTest {

    private lateinit var displayer: InAppMessageViewDisplayerImpl
    private lateinit var failureTracker: InAppFailureTracker

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

    @Test
    fun `default callback is ComposableInAppCallback`() {
        assertTrue(
            "Default callback should be ComposableInAppCallback",
            displayer.currentCallback() is ComposableInAppCallback
        )
    }

    @Test
    fun `registerInAppCallback replaces default callback`() {
        val customCallback = mockk<InAppCallback>()

        displayer.registerInAppCallback(customCallback)

        assertSame(customCallback, displayer.currentCallback())
    }

    @Test
    fun `unregisterInAppCallback restores default ComposableInAppCallback`() {
        val customCallback = mockk<InAppCallback>()
        displayer.registerInAppCallback(customCallback)

        displayer.unregisterInAppCallback()

        assertTrue(
            "After unregister, callback should be restored to ComposableInAppCallback",
            displayer.currentCallback() is ComposableInAppCallback
        )
    }

    @Test
    fun `registerInAppCallback replaces previously registered callback`() {
        val callbackA = mockk<InAppCallback>()
        val callbackB = mockk<InAppCallback>()

        displayer.registerInAppCallback(callbackA)
        displayer.registerInAppCallback(callbackB)

        assertSame(callbackB, displayer.currentCallback())
        assertNotSame(callbackA, displayer.currentCallback())
    }

    @Test
    fun `unregisterInAppCallback after multiple registers restores default`() {
        displayer.registerInAppCallback(mockk())
        displayer.registerInAppCallback(mockk())

        displayer.unregisterInAppCallback()

        assertTrue(displayer.currentCallback() is ComposableInAppCallback)
    }

    @Test
    fun `reattach presentation failure propagates restored holder tags`() {
        val expectedTags = mapOf("templateType" to "Popup", "campaign" to "summer")
        val inAppId = "reattach-inapp-id"

        val restoredHolder = mockk<InAppViewHolder<*>>(relaxed = true)
        every { restoredHolder.canReuseOnRestore(inAppId) } returns true
        every { restoredHolder.wrapper.tags } returns expectedTags

        // pausedHolder present, currentActivity stays null -> root is null -> reattach failure branch
        displayer.setPrivateField("pausedHolder", restoredHolder)

        val reattached = displayer.invokePrivateWithString("tryReattachRestoredInApp", inAppId) as Boolean

        assertTrue("reattach should be attempted for a reusable paused holder", reattached)
        verify(exactly = 1) {
            failureTracker.sendFailure(
                inAppId = inAppId,
                failureReason = FailureReason.PRESENTATION_FAILED,
                errorDetails = "failed to reattach inApp: currentRoot is null",
                tags = expectedTags,
            )
        }
    }

    // Accesses the private inAppCallback field via reflection
    private fun InAppMessageViewDisplayerImpl.currentCallback(): InAppCallback {
        val field = InAppMessageViewDisplayerImpl::class.java.getDeclaredField("inAppCallback")
        field.isAccessible = true
        return field.get(this) as InAppCallback
    }

    private fun InAppMessageViewDisplayerImpl.setPrivateField(name: String, value: Any?) {
        val field = InAppMessageViewDisplayerImpl::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(this, value)
    }

    private fun InAppMessageViewDisplayerImpl.invokePrivateWithString(name: String, arg: String): Any? {
        val method = InAppMessageViewDisplayerImpl::class.java.getDeclaredMethod(name, String::class.java)
        method.isAccessible = true
        return method.invoke(this, arg)
    }

    // ---- a candidate that will never be presented says so, so its hold in the show budgets can go back ----

    private class CountingCallbacks : InAppActionCallbacks {
        var notShown = 0
        override val onInAppClick = OnInAppClick {}
        override val onInAppShown = OnInAppShown {}
        override val onInAppDismiss = OnInAppDismiss {}
        override val onInAppNotShown = OnInAppNotShown { notShown++ }
    }

    @Test
    fun `a duplicate of a queued candidate is told it will not be shown`() {
        // No activity: the first candidate waits in the queue, the second is the same in-app.
        val first = CountingCallbacks()
        val duplicate = CountingCallbacks()
        val inApp = InAppStub.getInApp().form.variants.first()

        displayer.tryShowInAppMessage(inApp, first, {}, null)
        displayer.tryShowInAppMessage(inApp, duplicate, {}, null)

        assertEquals(0, first.notShown)
        assertEquals(1, duplicate.notShown)
    }

    @Test
    fun `closing the in-app discards the queue and tells every queued candidate`() {
        val queued = CountingCallbacks()
        displayer.tryShowInAppMessage(InAppStub.getInApp().form.variants.first(), queued, {}, null)

        val closeInApp = InAppMessageViewDisplayerImpl::class.java.getDeclaredMethod("closeInApp")
        closeInApp.isAccessible = true
        closeInApp.invoke(displayer)

        assertEquals(1, queued.notShown)
    }

    @Test
    fun `closing a presented in-app before it was shown tells it it will not be shown`() {
        // The holder's own failure paths (image, HTML fetch, WebView init timeout) end in
        // InAppController.close(); the hold in the show budgets must go back through that door.
        val callbacks = CountingCallbacks()
        val holder = mockk<InAppViewHolder<*>>(relaxed = true)
        every { holder.wrapper } returns InAppTypeWrapper(
            inAppType = InAppStub.getInApp().form.variants.first(),
            inAppActionCallbacks = callbacks,
            onRenderStart = {},
        )
        displayer.setPrivateField("currentHolder", holder)

        val closeInApp = InAppMessageViewDisplayerImpl::class.java.getDeclaredMethod("closeInApp")
        closeInApp.isAccessible = true
        closeInApp.invoke(displayer)

        assertEquals(1, callbacks.notShown)
        verify(exactly = 1) { holder.onClose() }
    }

    @Test
    fun `a restored in-app that cannot reattach is told it will not be shown`() {
        val callbacks = CountingCallbacks()
        val restoredHolder = mockk<InAppViewHolder<*>>(relaxed = true)
        every { restoredHolder.canReuseOnRestore("restored") } returns true
        every { restoredHolder.wrapper } returns InAppTypeWrapper(
            inAppType = InAppStub.getInApp().form.variants.first(),
            inAppActionCallbacks = callbacks,
            onRenderStart = {},
        )
        // pausedHolder present, no activity -> root is null -> the reattach failure branch
        displayer.setPrivateField("pausedHolder", restoredHolder)

        displayer.invokePrivateWithString("tryReattachRestoredInApp", "restored")

        assertEquals(1, callbacks.notShown)
    }
}
