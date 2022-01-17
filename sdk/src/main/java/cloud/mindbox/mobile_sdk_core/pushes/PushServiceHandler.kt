package cloud.mindbox.mobile_sdk_core.pushes

import android.content.Context

interface PushServiceHandler {

    fun initService(context: Context)

    fun subscribeToken(subscription: (String?) -> Unit): String

    fun disposeTokenSubscription(subscriptionId: String)

    fun getTokenSaveDate(): String

    fun updateToken(context: Context, token: String)

    fun deliverToken(token: String?)

    fun registerToken(): String?

    fun getAdsIdentification(context: Context): String

    fun ensureVersionCompatibility(context: Context, logParent: Any)

}