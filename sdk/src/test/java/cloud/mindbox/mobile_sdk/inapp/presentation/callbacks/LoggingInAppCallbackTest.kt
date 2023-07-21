package cloud.mindbox.mobile_sdk.inapp.presentation.callbacks

import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import io.mockk.every
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class LoggingInAppCallbackTest {

    @get:Rule
    val rule = MockKRule(this)
    private val id = "id"
    private val redirectUrl = "redirectUrl"
    private val payload = "payload"

    @OverrideMockKs
    private lateinit var loggingInAppCallback: LoggingInAppCallback

    @Before
    fun onTestStart() {
        mockkObject(MindboxLoggerImpl)
    }

    @Test
    fun `in app click logged`() {
        every {
            MindboxLoggerImpl.i(
                any(),
                "Click on InApp with id = $id, redirectUrl = $redirectUrl and payload = $payload"
            )
        } just runs
        loggingInAppCallback.onInAppClick(id = id, redirectUrl = redirectUrl, payload = payload)
        verify(exactly = 1) {
            MindboxLoggerImpl.i(
                any(),
                "Click on InApp with id = $id, redirectUrl = $redirectUrl and payload = $payload"
            )
        }
    }

    @Test
    fun `in app dismiss logged`() {
        every {
            MindboxLoggerImpl.i(
                any(),
                "Dismiss inApp with id = $id"
            )
        } just runs
        loggingInAppCallback.onInAppDismissed(id)
        verify(exactly = 1) {
            MindboxLoggerImpl.i(
                any(),
                "Dismiss inApp with id = $id"
            )
        }
    }

}