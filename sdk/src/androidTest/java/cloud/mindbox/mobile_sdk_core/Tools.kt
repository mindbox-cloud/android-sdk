package cloud.mindbox.mobile_sdk_core

import androidx.test.platform.app.InstrumentationRegistry
import cloud.mindbox.mobile_sdk_core.managers.SharedPreferencesManager
import cloud.mindbox.mobile_sdk_core.repository.MindboxDatabase

fun clearPreferences() {
    SharedPreferencesManager.deleteAll()
}

fun setDatabaseTestMode(isTestMode: Boolean) {
    MindboxDatabase.isTestMode = isTestMode
}

fun initCoreComponents() {
    MindboxInternalCore.initComponents(InstrumentationRegistry.getInstrumentation().targetContext) //for cancel method after test
}