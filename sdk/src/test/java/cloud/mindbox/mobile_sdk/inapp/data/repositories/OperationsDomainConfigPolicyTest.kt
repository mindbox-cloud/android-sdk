package cloud.mindbox.mobile_sdk.inapp.data.repositories

import cloud.mindbox.mobile_sdk.SdkValidation
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OperationsDomainConfigPolicyTest {

    @Before
    fun setUp() {
        mockkObject(SdkValidation)
        every { SdkValidation.isValidDomain(any()) } returns false
        every { SdkValidation.isValidDomain(VALID_HOST) } returns true
        every { SdkValidation.isValidDomain(VALID_HOST_WITH_SCHEME) } returns true
        every { SdkValidation.isValidDomain(VALID_HOST_WITH_TRAILING_SLASH) } returns true
        every { SdkValidation.isValidDomain(ANOTHER_VALID_HOST) } returns true
    }

    @After
    fun tearDown() {
        unmockkObject(SdkValidation)
    }

    // region raw null / empty — backend omitted value: clear if stored, keep if nothing to clear

    @Test
    fun `raw null stored null returns Keep`() {
        val result = operationsDomainConfigPolicyAction(raw = null, currentlyStored = null)

        assertEquals(OperationsDomainConfigPolicyAction.Keep, result)
    }

    @Test
    fun `raw null stored has value returns Clear`() {
        val result = operationsDomainConfigPolicyAction(raw = null, currentlyStored = VALID_HOST)

        assertEquals(OperationsDomainConfigPolicyAction.Clear, result)
    }

    @Test
    fun `raw empty stored has value returns Clear`() {
        val result = operationsDomainConfigPolicyAction(raw = "", currentlyStored = VALID_HOST)

        assertEquals(OperationsDomainConfigPolicyAction.Clear, result)
    }

    @Test
    fun `raw blank stored has value returns Clear`() {
        val result = operationsDomainConfigPolicyAction(raw = "   ", currentlyStored = VALID_HOST)

        assertEquals(OperationsDomainConfigPolicyAction.Clear, result)
    }

    // endregion

    // region invalid domain in config — spec 5.6: protect stored value

    @Test
    fun `raw invalid domain with stored value returns Keep — protect existing`() {
        val result = operationsDomainConfigPolicyAction(
            raw = "not a valid domain!!",
            currentlyStored = VALID_HOST
        )

        assertEquals(OperationsDomainConfigPolicyAction.Keep, result)
    }

    @Test
    fun `raw invalid domain no stored value returns Keep`() {
        val result = operationsDomainConfigPolicyAction(
            raw = "not a valid domain!!",
            currentlyStored = null
        )

        assertEquals(OperationsDomainConfigPolicyAction.Keep, result)
    }

    // endregion

    // region valid new domain — spec 3.1, 3.5

    @Test
    fun `raw valid domain no stored value returns Save`() {
        val result = operationsDomainConfigPolicyAction(raw = VALID_HOST, currentlyStored = null)

        assertEquals(OperationsDomainConfigPolicyAction.Save(VALID_HOST), result)
    }

    @Test
    fun `raw valid domain same as stored returns Keep`() {
        val result = operationsDomainConfigPolicyAction(
            raw = VALID_HOST,
            currentlyStored = VALID_HOST
        )

        assertEquals(OperationsDomainConfigPolicyAction.Keep, result)
    }

    @Test
    fun `raw valid domain different from stored returns Save — URL change on backend`() {
        val result = operationsDomainConfigPolicyAction(
            raw = ANOTHER_VALID_HOST,
            currentlyStored = VALID_HOST
        )

        assertEquals(OperationsDomainConfigPolicyAction.Save(ANOTHER_VALID_HOST), result)
    }

    // endregion

    // region scheme handling — spec 5.3, 5.4: store as-is

    @Test
    fun `raw with https scheme stored null returns Save with scheme preserved`() {
        val result = operationsDomainConfigPolicyAction(
            raw = VALID_HOST_WITH_SCHEME,
            currentlyStored = null
        )

        assertEquals(OperationsDomainConfigPolicyAction.Save(VALID_HOST_WITH_SCHEME), result)
    }

    @Test
    fun `raw with scheme same as stored returns Keep`() {
        val result = operationsDomainConfigPolicyAction(
            raw = VALID_HOST_WITH_SCHEME,
            currentlyStored = VALID_HOST_WITH_SCHEME
        )

        assertEquals(OperationsDomainConfigPolicyAction.Keep, result)
    }

    @Test
    fun `raw with trailing slash is saved as-is`() {
        val result = operationsDomainConfigPolicyAction(
            raw = VALID_HOST_WITH_TRAILING_SLASH,
            currentlyStored = null
        )

        // value is stored as-is; toBaseUrl() strips the slash when building the request URL
        assertEquals(OperationsDomainConfigPolicyAction.Save(VALID_HOST_WITH_TRAILING_SLASH), result)
    }

    @Test
    fun `raw with trailing slash same as stored returns Keep`() {
        val result = operationsDomainConfigPolicyAction(
            raw = VALID_HOST_WITH_TRAILING_SLASH,
            currentlyStored = VALID_HOST_WITH_TRAILING_SLASH
        )

        assertEquals(OperationsDomainConfigPolicyAction.Keep, result)
    }

    // endregion

    // region whitespace trimming

    @Test
    fun `raw with leading trailing whitespace is trimmed before comparison`() {
        val result = operationsDomainConfigPolicyAction(
            raw = "  $VALID_HOST  ",
            currentlyStored = VALID_HOST
        )

        // trimmed value equals stored → Keep
        assertEquals(OperationsDomainConfigPolicyAction.Keep, result)
    }

    @Test
    fun `raw with whitespace trimmed value is saved`() {
        val result = operationsDomainConfigPolicyAction(
            raw = "  $VALID_HOST  ",
            currentlyStored = null
        )

        assertEquals(OperationsDomainConfigPolicyAction.Save(VALID_HOST), result)
    }

    // endregion

    private companion object {
        const val VALID_HOST = "anonymizer.client.ru"
        const val VALID_HOST_WITH_SCHEME = "https://anonymizer.client.ru"
        const val VALID_HOST_WITH_TRAILING_SLASH = "https://anonymizer.client.ru/"
        const val ANOTHER_VALID_HOST = "new-anonymizer.client.ru"
    }
}
