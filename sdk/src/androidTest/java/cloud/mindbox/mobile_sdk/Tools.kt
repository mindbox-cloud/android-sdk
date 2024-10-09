package cloud.mindbox.mobile_sdk

import androidx.test.platform.app.InstrumentationRegistry
import cloud.mindbox.mobile_sdk.managers.SharedPreferencesManager
import cloud.mindbox.mobile_sdk.repository.MindboxDatabase

fun clearPreferences() = SharedPreferencesManager.deleteAll()

fun setDatabaseTestMode(isTestMode: Boolean) {
    MindboxDatabase.isTestMode = isTestMode
}

fun initCoreComponents() {
    // for cancel method after test
    Mindbox.initComponents(InstrumentationRegistry.getInstrumentation().targetContext, null)
}
