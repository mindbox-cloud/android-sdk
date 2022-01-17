package cloud.mindbox.mobile_sdk_core

import cloud.mindbox.mobile_sdk_core.managers.SharedPreferencesManager

internal fun clearPreferences() {
    SharedPreferencesManager.deleteAll()
}
