package cloud.mindbox.mobile_sdk.utils

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.checkers.Checker
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CheckerUtilsTest {
    private lateinit var checker1: Checker
    private lateinit var checker2: Checker

    @Before
    fun setup() {
        checker1 = mockk()
        checker2 = mockk()
    }

    @Test
    fun `returns true when all checkers return true`() {
        every { checker1.check() } returns true
        every { checker2.check() } returns true

        val result = allAllow(checker1, checker2)

        assertTrue(result)
    }

    @Test
    fun `returns false when first checker returns false`() {
        every { checker1.check() } returns false
        every { checker2.check() } returns true

        val result = allAllow(checker1, checker2)

        assertFalse(result)
    }

    @Test
    fun `returns false when second checker returns false`() {
        every { checker1.check() } returns true
        every { checker2.check() } returns false

        val result = allAllow(checker1, checker2)

        assertFalse(result)
    }

    @Test
    fun `returns false when all checkers return false`() {
        every { checker1.check() } returns false
        every { checker2.check() } returns false

        val result = allAllow(checker1, checker2)

        assertFalse(result)
    }

    @Test
    fun `returns true when no checkers are provided`() {
        val result = allAllow()

        assertTrue(result)
    }

    @Test
    fun `returns true when any checker throws exception`() {
        every { checker1.check() } throws RuntimeException("Test exception")
        every { checker2.check() } returns true

        val result = allAllow(checker1, checker2)

        assertTrue(result)
    }
}
