package cloud.mindbox.mobile_sdk.inapp.presentation

import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.inapp.presentation.callbacks.ComposableInAppCallback
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class InAppMessageViewDisplayerImplTest {

    private lateinit var displayer: InAppMessageViewDisplayerImpl

    @Before
    fun setUp() {
        mockkObject(MindboxDI)
        every { MindboxDI.appModule } returns mockk {
            every { gson } returns Gson()
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

    // Accesses the private inAppCallback field via reflection
    private fun InAppMessageViewDisplayerImpl.currentCallback(): InAppCallback {
        val field = InAppMessageViewDisplayerImpl::class.java.getDeclaredField("inAppCallback")
        field.isAccessible = true
        return field.get(this) as InAppCallback
    }
}
