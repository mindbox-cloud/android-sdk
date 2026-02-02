package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.inapp.presentation.view.BridgeMessage
import cloud.mindbox.mobile_sdk.inapp.presentation.view.WebViewAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeMessageValidatorTest {
    private val validator: BridgeMessageValidator = BridgeMessageValidator()

    @Test
    fun `isValid returns false for null message`() {
        val actualResult: Boolean = validator.isValid(null)
        assertFalse(actualResult)
    }

    @Test
    fun `isValid returns false for blank id`() {
        val message: BridgeMessage.Request = createRequest(id = " ")
        val actualResult: Boolean = validator.isValid(message)
        assertFalse(actualResult)
    }

    @Test
    fun `isValid returns false for unsupported type`() {
        val message: BridgeMessage.Request = createRequest(type = "unsupported")
        val actualResult: Boolean = validator.isValid(message)
        assertFalse(actualResult)
    }

    @Test
    fun `isValid returns false for non-positive timestamp`() {
        val zeroTimestampMessage: BridgeMessage.Request = createRequest(timestamp = 0L)
        val negativeTimestampMessage: BridgeMessage.Request = createRequest(timestamp = -1L)
        val zeroTimestampResult: Boolean = validator.isValid(zeroTimestampMessage)
        val negativeTimestampResult: Boolean = validator.isValid(negativeTimestampMessage)
        assertFalse(zeroTimestampResult)
        assertFalse(negativeTimestampResult)
    }

    @Test
    fun `isValid returns false for unsupported version`() {
        val message: BridgeMessage.Request = createRequest(version = BridgeMessage.VERSION + 1)
        val actualResult: Boolean = validator.isValid(message)
        assertFalse(actualResult)
    }

    @Test
    fun `isValid returns true for valid request message`() {
        val message: BridgeMessage.Request = createRequest()
        val actualResult: Boolean = validator.isValid(message)
        assertTrue(actualResult)
    }

    @Test
    fun `isValid returns false when reflection sets null id`() {
        val message: BridgeMessage.Request = createRequest()
        setFieldValue(target = message, fieldName = "id", value = null)
        val actualResult: Boolean = validator.isValid(message)
        assertFalse(actualResult)
    }

    private fun createRequest(
        id: String = "request-id",
        type: String = BridgeMessage.TYPE_REQUEST,
        version: Int = BridgeMessage.VERSION,
        timestamp: Long = 1L,
    ): BridgeMessage.Request {
        return BridgeMessage.Request(
            version = version,
            action = WebViewAction.INIT,
            payload = BridgeMessage.EMPTY_PAYLOAD,
            id = id,
            timestamp = timestamp,
            type = type,
        )
    }

    private fun setFieldValue(target: Any, fieldName: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }
}
