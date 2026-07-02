package cloud.mindbox.mobile_sdk.inapp.presentation

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
}
