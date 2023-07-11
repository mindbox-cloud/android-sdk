package cloud.mindbox.mobile_sdk.inapp.presentation

import android.content.ClipData
import android.content.Context

internal class ClipboardManagerImpl(private val context: Context) : ClipboardManager {

    override fun copyToClipboard(copyString: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(
            ClipData.newPlainText(
                PAYLOAD_LABEL,
                copyString
            )
        )
    }


    companion object {
        private const val PAYLOAD_LABEL = "payload"
    }
}