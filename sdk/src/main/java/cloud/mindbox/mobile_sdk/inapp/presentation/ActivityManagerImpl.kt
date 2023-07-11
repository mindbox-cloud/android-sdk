package cloud.mindbox.mobile_sdk.inapp.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.CallbackInteractor


internal class ActivityManagerImpl(
    private val callbackInteractor: CallbackInteractor,
    private val context: Context
) : ActivityManager {


    override fun tryOpenUrl(url: String): Boolean {
        try {
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).also { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return if (callbackInteractor.isValidUrl(url)) {
                    context.startActivity(intent)
                    true
                } else false
            }
        } catch (e: Exception) {
            return false
        }
    }

    override fun tryOpenDeepLink(deepLink: String): Boolean {
        Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).also { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else false
        }
    }
}