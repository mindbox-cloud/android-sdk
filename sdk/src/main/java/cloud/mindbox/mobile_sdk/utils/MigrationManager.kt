package cloud.mindbox.mobile_sdk.utils

import android.content.Context
import cloud.mindbox.mobile_sdk.InitializeLock
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.fromJsonTyped
import cloud.mindbox.mobile_sdk.logger.MindboxLog
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.managers.SharedPreferencesManager
import cloud.mindbox.mobile_sdk.pushes.PrefPushToken
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.toJsonTyped
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class MigrationManager(val context: Context) : MindboxLog {

    @Volatile
    private var isMigrating = false

    private val gson by mindboxInject { gson }

    suspend fun migrateAll() {
        if (isMigrating) return
        mindboxLogI("Check migrations needed")

        if (MindboxPreferences.isFirstInitialize) {
            MindboxPreferences.versionCode = Constants.SDK_VERSION_CODE
        }

        isMigrating = true
        listOf(
            version290(),
            version2120(),
            version2140()
        ).filter { it.isNeeded }
            .onEach { migration ->
                val job = Mindbox.mindboxScope.launch {
                    if (migration.isNeeded) {
                        logI("Run migration '${migration.description}'")
                        migration.run()
                    }
                }
                job.join()
            }.also {
                if (MindboxPreferences.versionCode != Constants.SDK_VERSION_CODE) {
                    logE("Migrations failed, reset memory")
                    MindboxPreferences.softReset()
                    MindboxPreferences.versionCode = Constants.SDK_VERSION_CODE
                }
            }

        InitializeLock.complete(InitializeLock.State.MIGRATION)
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
        val VERSION_CODE = 2

        override val description: String
            get() = "Changes the push token save format to multiple tokens with providers."
        override val isNeeded: Boolean
            get() = (MindboxPreferences.versionCode ?: 0) < VERSION_CODE

        override suspend fun run() {
            val provider = SharedPreferencesManager.getString("key_notification_provider")
            val token = SharedPreferencesManager.getString("key_firebase_token")
            val updateDate = SharedPreferencesManager.getString("key_firebase_token_save_date")

            SharedPreferencesManager.remove("key_notification_provider")
            SharedPreferencesManager.remove("key_firebase_token")
            SharedPreferencesManager.remove("key_firebase_token_save_date")
            val savedTokens = MindboxPreferences.pushTokens
            if (token != null && provider != null && savedTokens.isEmpty()) {
                val timestamp: Long? = updateDate?.let {
                    SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH).parse(it)?.time
                }
                MindboxPreferences.pushTokens = mapOf(provider to PrefPushToken(token, timestamp ?: Date().time))
            }

            MindboxPreferences.versionCode = VERSION_CODE
        }
    }

    private fun version2140() = object : Migration {
        val VERSION_CODE = 3

        override val description: String
            get() = "Changes the format of shown in-app messages from Map<String, Long> to Map<String, List<Long>>"
        override val isNeeded: Boolean
            get() = (MindboxPreferences.versionCode ?: 0) < VERSION_CODE

        override suspend fun run() {
            val oldShownInApps = loggingRunCatching(emptyMap()) {
                gson.fromJsonTyped<Map<String, Long>>(
                    MindboxPreferences.shownInApps
                ) ?: emptyMap()
            }

            val newShownInApps = oldShownInApps.mapValues { (_, timestamp) ->
                listOf(timestamp)
            }

            val newMapString = LoggingExceptionHandler.runCatching("") {
                gson.toJsonTyped<Map<String, List<Long>>>(newShownInApps)
            }

            MindboxPreferences.shownInApps = newMapString
            SharedPreferencesManager.remove("SHOWN_IDS")
            MindboxPreferences.versionCode = VERSION_CODE
        }
    }
}
