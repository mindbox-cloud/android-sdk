package cloud.mindbox.mobile_sdk.services

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.logOnException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class MindboxPushReceiver : BroadcastReceiver() {

    companion object {

        internal const val ACTION_CLICKED = "cloud.mindbox.mobile_sdk.PUSH_CLICKED"

        internal const val EXTRA_NOTIFICATION_ID = "notification_id"
        internal const val EXTRA_URL = "push_url"
        internal const val EXTRA_UNIQ_PUSH_KEY = "uniq_push_key"
        internal const val EXTRA_UNIQ_PUSH_BUTTON_KEY = "uniq_push_button_key"

        internal fun getIntent(
            context: Context,
            id: Int,
            action: String,
            pushKey: String,
            pushButtonKey: String?
        ) = Intent(action).apply {
            putExtra(EXTRA_NOTIFICATION_ID, id)
            putExtra(EXTRA_UNIQ_PUSH_KEY, pushKey)
            putExtra(EXTRA_UNIQ_PUSH_BUTTON_KEY, pushButtonKey)
            `package` = context.packageName
        }

    }

    private val coroutineScope by lazy { CoroutineScope(Dispatchers.IO) }

    override fun onReceive(context: Context, intent: Intent) = runCatching {
        handleIntent(context, intent)
    }.logOnException()

    private fun handleIntent(context: Context, intent: Intent) {
        coroutineScope.launch {
            val pushKey = intent.getStringExtra(EXTRA_UNIQ_PUSH_KEY) ?: ""
            val pushButtonKey = intent.getStringExtra(EXTRA_UNIQ_PUSH_BUTTON_KEY)
            Mindbox.onPushClicked(context, pushKey, pushButtonKey)
        }

        dismissNotification(context, intent)
        context.sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
    }

    private fun dismissNotification(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId != -1) {
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }
    }

}
