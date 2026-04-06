package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.view.KeyEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackButtonHandlerTest {

    @Test
    fun `dispatchKeyEvent returns true and invokes listener for non canceled back action up event`() {
        var hasInvokedListener = false
        val backButtonHandler = BackButtonHandler {
            hasInvokedListener = true
        }
        val event: KeyEvent = createKeyEvent(
            eventAction = KeyEvent.ACTION_UP,
            eventKeyCode = KeyEvent.KEYCODE_BACK,
            isEventCanceled = false,
        )
        val actualResult: Boolean? = backButtonHandler.dispatchKeyEvent(event)
        assertTrue(actualResult == true)
        assertTrue(hasInvokedListener)
    }

    @Test
    fun `dispatchKeyEvent returns null and does not invoke listener for back action down event`() {
        var hasInvokedListener = false
        val backButtonHandler = BackButtonHandler {
            hasInvokedListener = true
        }
        val event: KeyEvent = createKeyEvent(
            eventAction = KeyEvent.ACTION_DOWN,
            eventKeyCode = KeyEvent.KEYCODE_BACK,
            isEventCanceled = false,
        )
        val actualResult: Boolean? = backButtonHandler.dispatchKeyEvent(event)
        assertNull(actualResult)
        assertFalse(hasInvokedListener)
    }

    @Test
    fun `dispatchKeyEvent returns null and does not invoke listener for canceled back action up event`() {
        var hasInvokedListener = false
        val backButtonHandler = BackButtonHandler {
            hasInvokedListener = true
        }
        val event: KeyEvent = createKeyEvent(
            eventAction = KeyEvent.ACTION_UP,
            eventKeyCode = KeyEvent.KEYCODE_BACK,
            isEventCanceled = true,
        )
        val actualResult: Boolean? = backButtonHandler.dispatchKeyEvent(event)
        assertNull(actualResult)
        assertFalse(hasInvokedListener)
    }

    @Test
    fun `dispatchKeyEvent returns null and does not invoke listener for non back action up event`() {
        var hasInvokedListener = false
        val backButtonHandler = BackButtonHandler {
            hasInvokedListener = true
        }
        val event: KeyEvent = createKeyEvent(
            eventAction = KeyEvent.ACTION_UP,
            eventKeyCode = KeyEvent.KEYCODE_ENTER,
            isEventCanceled = false,
        )
        val actualResult: Boolean? = backButtonHandler.dispatchKeyEvent(event)
        assertNull(actualResult)
        assertFalse(hasInvokedListener)
    }

    @Test
    fun `dispatchKeyEvent returns null and does not invoke listener for null event`() {
        var hasInvokedListener = false
        val backButtonHandler = BackButtonHandler {
            hasInvokedListener = true
        }
        val actualResult: Boolean? = backButtonHandler.dispatchKeyEvent(event = null)
        assertNull(actualResult)
        assertFalse(hasInvokedListener)
    }

    private fun createKeyEvent(eventAction: Int, eventKeyCode: Int, isEventCanceled: Boolean): KeyEvent {
        return mockk<KeyEvent> {
            every { action } returns eventAction
            every { keyCode } returns eventKeyCode
            every { isCanceled } returns isEventCanceled
        }
    }
}
