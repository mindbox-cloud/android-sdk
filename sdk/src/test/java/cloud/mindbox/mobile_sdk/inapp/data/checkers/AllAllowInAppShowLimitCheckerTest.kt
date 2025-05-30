package cloud.mindbox.mobile_sdk.inapp.data.checkers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.checkers.InAppShowLimitChecker
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AllAllowInAppShowLimitCheckerTest {

    private lateinit var checker1: InAppShowLimitChecker
    private lateinit var checker2: InAppShowLimitChecker
    private lateinit var allAllowInAppShowLimitChecker: AllAllowInAppShowLimitChecker

    @Before
    fun setup() {
        checker1 = mockk()
        checker2 = mockk()
        allAllowInAppShowLimitChecker = AllAllowInAppShowLimitChecker(listOf(checker1, checker2))
    }

    @Test
    fun `check returns true when all checkers return true`() {
        every { checker1.check() } returns true
        every { checker2.check() } returns true

        val result = allAllowInAppShowLimitChecker.check()

        assertTrue(result)
    }

    @Test
    fun `check returns false when first checker returns false`() {
        every { checker1.check() } returns false
        every { checker2.check() } returns true

        val result = allAllowInAppShowLimitChecker.check()

        assertFalse(result)
    }

    @Test
    fun `check returns false when second checker returns false`() {
        every { checker1.check() } returns true
        every { checker2.check() } returns false

        val result = allAllowInAppShowLimitChecker.check()

        assertFalse(result)
    }

    @Test
    fun `check returns false when all checkers return false`() {
        every { checker1.check() } returns false
        every { checker2.check() } returns false

        val result = allAllowInAppShowLimitChecker.check()

        assertFalse(result)
    }

    @Test
    fun `check returns true when no checkers provided`() {
        val emptyChecker = AllAllowInAppShowLimitChecker(emptyList())

        val result = emptyChecker.check()

        assertTrue(result)
    }

    @Test
    fun `check returns true when any checker throws exception`() {
        every { checker1.check() } returns true
        every { checker2.check() } throws RuntimeException("Test exception")

        val result = allAllowInAppShowLimitChecker.check()

        assertTrue(result)
    }

    @Test
    fun `second checker is not invoked when first checker returns false`() {
        every { checker1.check() } returns false
        every { checker2.check() } returns true

        val result = allAllowInAppShowLimitChecker.check()

        verify(exactly = 1) { checker1.check() }
        verify(exactly = 0) { checker2.check() }
        assertFalse(result)
    }
}
