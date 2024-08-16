package cloud.mindbox.mobile_sdk.inapp.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.interactors.CallbackInteractor
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler


internal class ActivityManagerImpl(
    private val callbackInteractor: CallbackInteractor,
    private val context: Context
) : ActivityManager {

    companion object {
        private const val ALL_MATCHING_ACTIVITIES_FLAG: Int = 0
    }

    override fun tryOpenUrl(url: String): Boolean {
        try {
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).also { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return if (callbackInteractor.isValidUrl(url) && intent.resolveActivity(context.packageManager) == null) {
                    context.startActivity(intent)
                    true
                } else false
            }
        } catch (e: Exception) {
            return false
        }
    }

    override fun tryOpenDeepLink(deepLink: String): Boolean =
        LoggingExceptionHandler.runCatching(
            block = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    if (intent.resolveActivityInfo(
                            context.packageManager,
                            ALL_MATCHING_ACTIVITIES_FLAG
                        )?.exported == false
                    ) {
                        intent.`package` = context.packageName
                    }
                    context.startActivity(intent)
                    true
                } else {
                    false
                }
            },
            defaultValue = {
                false
            }
        )
}