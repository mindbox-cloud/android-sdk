package cloud.mindbox.mobile_sdk.inapp.presentation.view

import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(InternalMindboxApi::class)
class WebViewPermissionBridgeSerializationTest {

    private val gson: Gson = Gson()

    @Test
    fun `toJson serializes denied result correctly`() {
        val payload = PermissionActionResponse(
            result = PermissionRequestStatus.DENIED,
            dialogShown = true
        )
        val json: String = gson.toJson(payload)
        val parsedPayload: PermissionResponseTestPayload = gson.fromJson(json, PermissionResponseTestPayload::class.java)
        assertEquals("denied", parsedPayload.result)
        assertEquals(true, parsedPayload.dialogShown)
    }

    @Test
    fun `fromJson maps permission request action to enum`() {
        val message: ActionWrapper = gson.fromJson("""{"action":"permission.request"}""", ActionWrapper::class.java)
        assertEquals(WebViewAction.PERMISSION_REQUEST, message.action)
    }

    private data class PermissionResponseTestPayload(
        val result: String,
        val dialogShown: Boolean
    )

    private data class ActionWrapper(
        val action: WebViewAction
    )
}
