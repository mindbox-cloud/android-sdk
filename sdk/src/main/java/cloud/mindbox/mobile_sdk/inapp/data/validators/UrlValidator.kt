package cloud.mindbox.mobile_sdk.inapp.data.validators

import android.webkit.URLUtil

internal class UrlValidator : Validator<String?> {

    override fun isValid(item: String?): Boolean {
        return URLUtil.isHttpUrl(item) || URLUtil.isHttpsUrl(item)
    }
}