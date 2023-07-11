package cloud.mindbox.mobile_sdk.inapp.presentation.callbacks

import cloud.mindbox.mobile_sdk.inapp.presentation.InAppCallback
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class ComposableInAppCallbackTest {

    private lateinit var mockCallback1: InAppCallback
    private lateinit var mockCallback2: InAppCallback
    private lateinit var callback: ComposableInAppCallback


    @Before
    fun setup() {
        mockCallback1 = mockk(relaxed = true)
        mockCallback2 = mockk(relaxed = true)
        callback = spyk(ComposableInAppCallback(mutableListOf(mockCallback1, mockCallback2)))
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `onInAppClick should call onInAppClick on all callbacks`() {
        // Arrange
        val id = "testId"
        val redirectUrl = "testRedirectUrl"
        val payload = "testPayload"

        // Act
        callback.onInAppClick(id, redirectUrl, payload)

        // Assert
        verify(exactly = 1) { mockCallback1.onInAppClick(id, redirectUrl, payload) }
        verify(exactly = 1) { mockCallback2.onInAppClick(id, redirectUrl, payload) }
    }

    @Test
    fun `onInAppDismissed should call onInAppDismissed on all callbacks`() {
        // Arrange
        val id = "testId"

        // Act
        callback.onInAppDismissed(id)

        // Assert
        verify(exactly = 1) { mockCallback1.onInAppDismissed(id) }
        verify(exactly = 1) { mockCallback2.onInAppDismissed(id) }
    }

}