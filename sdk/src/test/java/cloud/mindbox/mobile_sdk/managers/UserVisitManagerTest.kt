package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule

import org.junit.Test

internal class UserVisitManagerTest {

    @get:Rule
    val rule = MockKRule(this)

    @Before
    fun onTestStart() {
        mockkObject(MindboxPreferences)
    }

    private val userVisitManager = UserVisitManagerImpl()
    @Test
    fun `save user visit count when visit count more than 0`() {
        val previousUserCount = 1
        val newUserCount = 2
        every {
            MindboxPreferences.userVisitCount = any()
        } just runs
        every {
            MindboxPreferences.userVisitCount
        } returns previousUserCount
        userVisitManager.saveUserVisit()
        verify(exactly = 1) {
            MindboxPreferences.userVisitCount = newUserCount
        }
    }

    @Test
    fun `save user visit count when visit count equals 0 and initialization is first`() {
        val previousUserCount = 0
        val newUserCount = 1
        every {
            MindboxPreferences.userVisitCount = any()
        } just runs
        every {
            MindboxPreferences.isFirstInitialize
        } returns true
        every {
            MindboxPreferences.userVisitCount
        } returns previousUserCount
        userVisitManager.saveUserVisit()
        verify(exactly = 1) {
            MindboxPreferences.userVisitCount = newUserCount
        }
    }

    @Test
    fun `save user visit count when visit count equals 0 and initialization is not first`() {
        val previousUserCount = 0
        val newUserCount = 2
        every {
            MindboxPreferences.userVisitCount = any()
        } just runs
        every {
            MindboxPreferences.isFirstInitialize
        } returns false
        every {
            MindboxPreferences.userVisitCount
        } returns previousUserCount
        userVisitManager.saveUserVisit()
        verify(exactly = 1) {
            MindboxPreferences.userVisitCount = newUserCount
        }
    }
}