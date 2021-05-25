package cloud.mindbox.mobile_sdk

import cloud.mindbox.mobile_sdk.managers.SharedPreferencesManager

internal fun clearPreferences() {
    SharedPreferencesManager.deleteAll()
}
