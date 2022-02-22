package cloud.mindbox.mobile_sdk.repository

import cloud.mindbox.mobile_sdk.managers.SharedPreferencesManager
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import java.util.*

internal object MindboxPreferences {

    private const val KEY_IS_FIRST_INITIALIZATION = "key_is_first_initialization"
    private const val KEY_DEVICE_UUID = "key_device_uuid"
    private const val KEY_PUSH_TOKEN = "key_firebase_token"
    private const val KEY_FIREBASE_TOKEN_SAVE_DATE = "key_firebase_token_save_date"
    private const val KEY_IS_NOTIFICATION_ENABLED = "key_is_notification_enabled"
    private const val KEY_HOST_APP_MANE =
        "key_host_app_name" //need for scheduling and stopping one-time background service
    private const val KEY_INFO_UPDATED_VERSION = "key_info_updated_version"
    private const val KEY_INSTANCE_ID = "key_instance_id"
    private const val DEFAULT_INFO_UPDATED_VERSION = 1

    var isFirstInitialize: Boolean
        get() = LoggingExceptionHandler.runCatching(defaultValue = true) {
            SharedPreferencesManager.getBoolean(KEY_IS_FIRST_INITIALIZATION, true)
        }
        set(value) {
            LoggingExceptionHandler.runCatching {
                SharedPreferencesManager.put(KEY_IS_FIRST_INITIALIZATION, value)
            }
        }

    var deviceUuid: String
        get() = LoggingExceptionHandler.runCatching(defaultValue = "") {
            SharedPreferencesManager.getString(KEY_DEVICE_UUID) ?: ""
        }
        set(value) {
            LoggingExceptionHandler.runCatching {
                SharedPreferencesManager.put(KEY_DEVICE_UUID, value)
            }
        }

    var pushToken: String?
        get() = LoggingExceptionHandler.runCatching(defaultValue = null) {
            SharedPreferencesManager.getString(KEY_PUSH_TOKEN)
        }
        set(value) {
            LoggingExceptionHandler.runCatching {
                SharedPreferencesManager.put(KEY_PUSH_TOKEN, value)
                tokenSaveDate = Date().toString()
            }
        }

    var tokenSaveDate: String
        get() = LoggingExceptionHandler.runCatching(defaultValue = "") {
             SharedPreferencesManager.getString(KEY_FIREBASE_TOKEN_SAVE_DATE) ?: ""
        }
        set(value) {
            LoggingExceptionHandler.runCatching {
                SharedPreferencesManager.put(KEY_FIREBASE_TOKEN_SAVE_DATE, value)
            }
        }

    var isNotificationEnabled: Boolean
        get() = LoggingExceptionHandler.runCatching(defaultValue = true) {
            SharedPreferencesManager.getBoolean(KEY_IS_NOTIFICATION_ENABLED, true)
        }
        set(value) {
            LoggingExceptionHandler.runCatching {
                SharedPreferencesManager.put(KEY_IS_NOTIFICATION_ENABLED, value)
            }
        }

    var hostAppName: String
        get() = LoggingExceptionHandler.runCatching(defaultValue = "") {
            SharedPreferencesManager.getString(KEY_HOST_APP_MANE) ?: ""
        }
        set(value) {
            LoggingExceptionHandler.runCatching {
                SharedPreferencesManager.put(KEY_HOST_APP_MANE, value)
            }
        }

    val infoUpdatedVersion: Int
        @Synchronized get() = LoggingExceptionHandler.runCatching(
            defaultValue = DEFAULT_INFO_UPDATED_VERSION
        ) {
            val version = SharedPreferencesManager.getInt(
                KEY_INFO_UPDATED_VERSION,
                DEFAULT_INFO_UPDATED_VERSION
            )
            SharedPreferencesManager.put(KEY_INFO_UPDATED_VERSION, version + 1)
            version
        }

    var instanceId: String
        get() = LoggingExceptionHandler.runCatching(defaultValue = "") {
            SharedPreferencesManager.getString(KEY_INSTANCE_ID) ?: ""
        }
        set(value) {
            LoggingExceptionHandler.runCatching {
                SharedPreferencesManager.put(KEY_INSTANCE_ID, value)
            }
        }

}
