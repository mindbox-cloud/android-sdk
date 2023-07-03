package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.CallbackRepository
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CallbackInteractorTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var callbackRepository: CallbackRepository

    @OverrideMockKs
    private lateinit var callbackInteractor: CallbackInteractorImpl

    @Test
    fun `should copy string user string is valid`() {
        every {
            callbackRepository.validateUserString(any())
        } returns true
        val validUserString = "validUserString"
        assertTrue(callbackInteractor.shouldCopyString(validUserString))
    }

    @Test
    fun `should copy string user string is invalid`() {
        every {
            callbackRepository.validateUserString(any())
        } returns false
        val inValidUserString = "{\"name\":\"John\",\"age\":30}"
        assertFalse(callbackInteractor.shouldCopyString(inValidUserString))
    }

    @Test
    fun `url is valid`() {
        val userString = "https://www.example.com"
        every {
            callbackRepository.isValidUrl(any())
        } returns true
        assertTrue(callbackInteractor.isValidUrl(userString))
    }

    @Test
    fun `url is inValid`() {
        val userString = "https://www.example.com"
        every {
            callbackRepository.isValidUrl(any())
        } returns false
        assertFalse(callbackInteractor.isValidUrl(userString))
    }
}