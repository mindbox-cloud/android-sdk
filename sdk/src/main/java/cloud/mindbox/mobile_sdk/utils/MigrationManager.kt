package cloud.mindbox.mobile_sdk.utils

import android.content.Context
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.managers.SharedPreferencesManager
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import kotlinx.coroutines.launch

internal class MigrationManager(val context: Context) {

    fun migrateAll() {
        listOf<Migration>(
            version282()
        ).filter { it.isNeeded }
            .onEach { migration ->
                loggingRunCatching {
                    mindboxLogI("Run migration '${migration.description}'")
                    migration.run()
                }
            }
    }

    private interface Migration {
        val description: String
        val isNeeded: Boolean
        
        fun run()
    }

    private fun version282() = object: Migration {

        override val description: String
            get() = "Updates the push notification token to resolve an issue with the push notification provider."

        override val isNeeded: Boolean
            get() = SharedPreferencesManager.isInitialized()
                    && !MindboxPreferences.isFirstInitialize
                    && MindboxPreferences.isPushTokenNeedUpdated

        override fun run() {
            Mindbox.mindboxScope.launch {
                MindboxPreferences.isPushTokenNeedUpdated = false
                Mindbox.updateAppInfo(context)
            }
        }
    }
}