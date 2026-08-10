package cloud.mindbox.mobile_sdk.embedded.webview

import android.view.View
import org.json.JSONObject

internal interface EmbeddedBlockPage {

    val view: View

    var onMessage: ((TempEmbeddedBlockPageMessage) -> Unit)?

    var onMechanicMessage: ((payload: JSONObject) -> Unit)?

    var onPageError: ((description: String) -> Unit)?

    fun load()

    fun pause()

    fun resume()

    fun release()
}
