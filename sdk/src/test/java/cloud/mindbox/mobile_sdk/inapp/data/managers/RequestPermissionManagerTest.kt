package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.managers.RequestPermissionManagerImpl
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RequestPermissionManagerTest {

    @Before
    fun setUp() {
        mockkObject(MindboxPreferences)
    }

    private val requestPermissionManager = RequestPermissionManagerImpl()

    @Test
    fun `increaseRequestCounter increments the counter by 1`() {
        val previousRequestCount = 0
        val newRequestCount = 1

        every {
            MindboxPreferences.requestPermissionCount = any()
        } just runs
        every {
            MindboxPreferences.requestPermissionCount
        } returns previousRequestCount

        requestPermissionManager.increaseRequestCounter()

        verify(exactly = 1) {
            MindboxPreferences.requestPermissionCount = newRequestCount
        }
    }

    @Test
    fun `decreaseRequestCounter decrements the counter by 1 when it is greater than 0`() {
        val previousRequestCount = 1
        val newRequestCount = 0

        every {
            MindboxPreferences.requestPermissionCount = any()
        } just runs
        every {
            MindboxPreferences.requestPermissionCount
        } returns previousRequestCount

        requestPermissionManager.decreaseRequestCounter()

        verify(exactly = 1) {
            MindboxPreferences.requestPermissionCount = newRequestCount
        }
    }

    @Test
    fun `decreaseRequestCounter does nothing when the counter is 0`() {
        val previousRequestCount = 0

        every {
            MindboxPreferences.requestPermissionCount = any()
        } just runs
        every {
            MindboxPreferences.requestPermissionCount
        } returns previousRequestCount

        requestPermissionManager.decreaseRequestCounter()

        verify(exactly = 0) {
            MindboxPreferences.requestPermissionCount = any()
        }
    }

    @Test
    fun `getCounterValue returns the current value of the counter`() {
        every { MindboxPreferences.requestPermissionCount } returns 5

        val result = requestPermissionManager.getRequestCount()

        assertEquals(5, result)
    }
}