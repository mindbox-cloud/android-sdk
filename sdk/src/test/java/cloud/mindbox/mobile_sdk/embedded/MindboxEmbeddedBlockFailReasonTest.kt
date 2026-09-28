package cloud.mindbox.mobile_sdk.embedded

import cloud.mindbox.mobile_sdk.models.operation.request.FailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MindboxEmbeddedBlockFailReasonTest {

    @Test
    fun `the two reasons carry the names shared with the other platforms`() {
        assertEquals("networkError", MindboxEmbeddedBlockFailReason.NETWORK_ERROR.value)
        assertEquals("internalError", MindboxEmbeddedBlockFailReason.INTERNAL_ERROR.value)
    }

    @Test
    fun `a reason prints as its name and is told apart from the other by the name alone`() {
        val networkError = MindboxEmbeddedBlockFailReason.NETWORK_ERROR
        val internalError = MindboxEmbeddedBlockFailReason.INTERNAL_ERROR

        assertEquals("networkError", networkError.toString())
        assertEquals(networkError, FailureReason.WAIT_BUDGET_EXCEEDED.toEmbeddedBlockFailReason())
        assertEquals(networkError.hashCode(), FailureReason.WAIT_BUDGET_EXCEEDED.toEmbeddedBlockFailReason().hashCode())
        assertNotEquals(networkError, internalError)
        assertNotEquals(networkError, "networkError")
    }

    @Test
    fun `no answer and a page that did not load are a network error`() {
        assertEquals(MindboxEmbeddedBlockFailReason.NETWORK_ERROR, FailureReason.WAIT_BUDGET_EXCEEDED.toEmbeddedBlockFailReason())
        assertEquals(MindboxEmbeddedBlockFailReason.NETWORK_ERROR, FailureReason.WEBVIEW_LOAD_FAILED.toEmbeddedBlockFailReason())
    }

    @Test
    fun `unusable content and every other code are an internal error`() {
        val networkCodes = setOf(FailureReason.WAIT_BUDGET_EXCEEDED, FailureReason.WEBVIEW_LOAD_FAILED)

        assertEquals(MindboxEmbeddedBlockFailReason.INTERNAL_ERROR, FailureReason.PRESENTATION_FAILED.toEmbeddedBlockFailReason())
        assertEquals(MindboxEmbeddedBlockFailReason.INTERNAL_ERROR, FailureReason.UNKNOWN_ERROR.toEmbeddedBlockFailReason())
        FailureReason.entries
            .filterNot { code -> code in networkCodes }
            .forEach { code ->
                assertEquals(code.name, MindboxEmbeddedBlockFailReason.INTERNAL_ERROR, code.toEmbeddedBlockFailReason())
            }
    }
}
