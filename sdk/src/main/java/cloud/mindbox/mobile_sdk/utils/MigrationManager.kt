package cloud.mindbox.mobile_sdk.utils

import android.content.Context
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.logger.MindboxLog
import cloud.mindbox.mobile_sdk.managers.SharedPreferencesManager
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class MigrationManager(val context: Context) : MindboxLog {

    private val migrationMutex = Mutex()
    private val migrationJobs = mutableListOf<Job>()

    suspend fun migrateAll() {
        listOf(
            version290(),
            version2120(),
        ).filter { it.isNeeded }
            .onEach { migration ->
                val job = Mindbox.mindboxScope.launch {
                    migrationMutex.withLock {
                        if (migration.isNeeded) {
                            logI("Run migration '${migration.description}'")
                            migration.run()
                        }
                    }
                }
                migrationJobs.add(job)
            }.also {
                migrationJobs.forEach { it.join() }
                if (MindboxPreferences.versionCode != Constants.SDK_VERSION_CODE) {
                    logE("Migrations failed, reset memory")
                    MindboxPreferences.softReset()
                    MindboxPreferences.versionCode = Constants.SDK_VERSION_CODE
                }
            }
    }

    private interface Migration {
        val description: String
        val isNeeded: Boolean

        suspend fun run()
    }

    private fun version290() = object : Migration {
        override val description: String
            get() = "Replaces set of shown inapps to map of inapp metadata"
        override val isNeeded: Boolean
            get() = MindboxPreferences.shownInAppIds != ""

        override suspend fun run() {
            val gson = Gson()
            val oldShownInApps = LoggingExceptionHandler.runCatching<Set<String>>(HashSet()) {
                gson.fromJson(
                    MindboxPreferences.shownInAppIds,
                    object : TypeToken<HashSet<String>>() {}.type
                ) ?: emptySet()
            }
            val newShownInApps = oldShownInApps.associateWith {
                0L
            }
            val newMapString = LoggingExceptionHandler.runCatching("") {
                gson.toJson(newShownInApps, object : TypeToken<HashMap<String, Long>>() {}.type)
            }
            MindboxPreferences.shownInApps = newMapString
            MindboxPreferences.shownInAppIds = ""
        }
    }

    private fun version2120() = object : Migration {
        override val description: String
            get() = "Changes the push token save format to multiple tokens with providers."
        override val isNeeded: Boolean
            get() = MindboxPreferences.versionCode < Constants.SDK_VERSION_CODE

        override suspend fun run() {
            val provider = SharedPreferencesManager.getString("KEY_PUSH_TOKEN")
            val token = SharedPreferencesManager.getString("KEY_PUSH_TOKEN")

            SharedPreferencesManager.remove("key_notification_provider")
            if (token != null && provider != null) {
                MindboxPreferences.pushTokens = mapOf(provider to token)
            }

            MindboxPreferences.versionCode++
        }
    }
}
