package cloud.mindbox.mobile_sdk.inapp.presentation

import android.app.Activity
import cloud.mindbox.mobile_sdk.inapp.domain.InAppMessageViewDisplayer
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import io.mockk.junit4.MockKRule
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
internal class InAppMessageViewDisplayerImplTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    private lateinit var inAppMessageViewDisplayerImpl: InAppMessageViewDisplayer

    @Before
    fun onTestStart() {
        inAppMessageViewDisplayerImpl = InAppMessageViewDisplayerImpl()
        mockkObject(MindboxLoggerImpl)
    }

    @Test
    fun `resume activity with blur`() = runTest {
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        inAppMessageViewDisplayerImpl.onResumeCurrentActivity(activity, true)
        assertNotNull(inAppMessageViewDisplayerImpl.currentRoot)
        assertNotNull(inAppMessageViewDisplayerImpl.currentBlur)
        assertNotNull(inAppMessageViewDisplayerImpl.currentDialog)
        assertNotNull(inAppMessageViewDisplayerImpl.currentActivity)
        verify(exactly = 1) {
            MindboxLoggerImpl.i(InAppMessageViewDisplayerImpl, "Enable blur")
        }
    }


    @Test
    fun `resume activity without blur`() = runTest {
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        inAppMessageViewDisplayerImpl.onResumeCurrentActivity(activity, false)
        assertNotNull(inAppMessageViewDisplayerImpl.currentRoot)
        assertNotNull(inAppMessageViewDisplayerImpl.currentBlur)
        assertNotNull(inAppMessageViewDisplayerImpl.currentDialog)
        assertNotNull(inAppMessageViewDisplayerImpl.currentActivity)
        verify(exactly = 1) {
            MindboxLoggerImpl.i(InAppMessageViewDisplayerImpl, "Disable blur")
        }
    }


    @Test
    fun `register current activity with blur`() = runTest {
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        inAppMessageViewDisplayerImpl.registerCurrentActivity(activity, true)
        assertNotNull(inAppMessageViewDisplayerImpl.currentRoot)
        assertNotNull(inAppMessageViewDisplayerImpl.currentBlur)
        assertNotNull(inAppMessageViewDisplayerImpl.currentDialog)
        assertNotNull(inAppMessageViewDisplayerImpl.currentActivity)
        verify(exactly = 1) {
            MindboxLoggerImpl.i(InAppMessageViewDisplayerImpl, "Enable blur")
        }
    }

    @Test
    fun `register current activity without blur`() = runTest {
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        inAppMessageViewDisplayerImpl.registerCurrentActivity(activity, false)
        assertNotNull(inAppMessageViewDisplayerImpl.currentRoot)
        assertNotNull(inAppMessageViewDisplayerImpl.currentBlur)
        assertNotNull(inAppMessageViewDisplayerImpl.currentDialog)
        assertNotNull(inAppMessageViewDisplayerImpl.currentActivity)
        verify(exactly = 1) {
            MindboxLoggerImpl.i(InAppMessageViewDisplayerImpl, "Disable blur")
        }
    }

    @Test
    fun `activity paused with correct activity`() = runTest {
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        inAppMessageViewDisplayerImpl.onResumeCurrentActivity(activity, true)
        inAppMessageViewDisplayerImpl.onPauseCurrentActivity(activity)
        assertNull(inAppMessageViewDisplayerImpl.currentRoot)
        assertNull(inAppMessageViewDisplayerImpl.currentBlur)
        assertNull(inAppMessageViewDisplayerImpl.currentDialog)
        assertNull(inAppMessageViewDisplayerImpl.currentActivity)
    }

    @Test
    fun `activity paused with incorrect activity`() = runTest {
        val firstActivity = Robolectric.buildActivity(Activity::class.java).get()
        val secondActivity = Robolectric.buildActivity(Activity::class.java).get()
        inAppMessageViewDisplayerImpl.onResumeCurrentActivity(firstActivity, true)
        inAppMessageViewDisplayerImpl.onPauseCurrentActivity(secondActivity)
        assertNull(inAppMessageViewDisplayerImpl.currentRoot)
        assertNull(inAppMessageViewDisplayerImpl.currentBlur)
        assertNull(inAppMessageViewDisplayerImpl.currentDialog)
        assertNotNull(inAppMessageViewDisplayerImpl.currentActivity)
    }

    @Test
    fun `register in-app callback`() = runTest {
        inAppMessageViewDisplayerImpl.registerInAppCallback(object : InAppCallback {
            override fun onInAppClick(id: String, redirectUrl: String, payload: String) {}
            override fun onInAppDismissed(id: String) {}
        })
        assertNotNull(inAppMessageViewDisplayerImpl.inAppCallback)
    }
}
