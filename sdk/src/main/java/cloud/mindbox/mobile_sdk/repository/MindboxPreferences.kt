package cloud.mindbox.mobile_sdk.repository

import cloud.mindbox.mobile_sdk.managers.SharedPreferencesManager
import cloud.mindbox.mobile_sdk.pushes.PrefPushTokenMap
import cloud.mindbox.mobile_sdk.pushes.toPreferences
import cloud.mindbox.mobile_sdk.pushes.toTokensMap
import cloud.mindbox.mobile_sdk.utils.Constants
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

internal object MindboxPreferences {

    private const val KEY_IS_FIRST_INITIALIZATION = "key_is_first_initialization"
    private const val KEY_DEVICE_UUID = "key_device_uuid"
    private const val KEY_PUSH_TOKENS = "key_push_tokens"
    private const val KEY_IS_NOTIFICATION_ENABLED = "key_is_notification_enabled"
    private const val KEY_HOST_APP_MANE =
        "key_host_app_name" // need for scheduling and stopping one-time background service
    private const val KEY_INFO_UPDATED_VERSION = "key_info_updated_version"
    private const val KEY_INSTANCE_ID = "key_instance_id"
    private const val KEY_UUID_DEBUG_ENABLED = "key_uuid_debug_enabled"
    private const val DEFAULT_INFO_UPDATED_VERSION = 1
    private const val IN_APP_CONFIG = "IN_APP_CONFIG"
    private const val SHOWN_IDS = "SHOWN_IDS"
    private const val IN_APP_GEO = "IN_APP_GEO"
    private const val LOGS_REQUEST_IDS = "LOGS_REQUEST_IDS"
    private const val KEY_USER_VISIT_COUNT = "key_user_visit_count"
    private const val KEY_REQUEST_PERMISSION_COUNT = "key_request_permission_count"
    private const val IN_APPS_METADATA = "key_inapp_metadata"
    private const val KEY_CONFIG_UPDATE_DATE = "key_config_update_date"
    private const val KEY_SDK_VERSION_CODE = "key_sdk_version_code"

    private val prefScope = CoroutineScope(Dispatchers.Default)

    fun softReset() {
        inAppConfig = ""
        shownInAppIds = ""
        inAppGeo = ""
        logsRequestIds = ""
        userVisitCount = 0
        requestPermissionCount = 0
        shownInApps = ""
        inAppConfigUpdatedTime = 0
    }

    var logsRequestIds: String
        get() = LoggingExceptionHandler.runCatching(defaultValue = "") {
            SharedPreferencesManager.getString(LOGS_REQUEST_IDS, "") ?: ""
        }
        set(value) {
            LoggingExceptionHandler.runCatching {
                SharedPreferencesManager.put(LOGS_REQUEST_IDS, value)
            }
        }

    var inAppGeo: String
        get() = LoggingExceptionHandler.runCatching(defaultValue = "") {
            SharedPreferencesManager.getString(IN_APP_GEO) ?: ""
        }
        set(value) {
            LoggingExceptionHandler.runCatching {
                SharedPreferencesManager.put(IN_APP_GEO, value)
            }
        }
    val inAppConfigFlow: MutableSharedFlow<String> = MutableSharedFlow(replay = 20)
    var inAppConfig: String
        get() = LoggingExceptionHandler.runCatching(defaultValue = "") {
            SharedPreferencesManager.getString(IN_APP_CONFIG) ?: ""
        }
        set(value) {
            LoggingExceptionHandler.runCatching {
                SharedPreferencesManager.put(IN_APP_CONFIG, value)
                prefScope.launch {
                    inAppConfigFlow.emit(value)
                }
            }
        }

    var shownInAppIds: String
        get() = LoggingExceptionHandler.runCatching(defaultValue = "") {
            SharedPreferencesManager.getString(SHOWN_IDS, "") ?: ""
        }
        set(value) {
            LoggingExceptionHandler.runCatching {
                SharedPreferencesManager.put(SHOWN_IDS, value)
            }
        }

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
                SharedPreferencesManager.putSync(KEY_DEVICE_UUID, value)
            }
        }

    var pushTokens: PrefPushTokenMap
        get() = loggingRunCatching(defaultValue = emptyMap()) {
            SharedPreferencesManager.getString(KEY_PUSH_TOKENS).toTokensMap()
        }
        set(value) {
            loggingRunCatching {
                SharedPreferencesManager.put(KEY_PUSH_TOKENS, value.toPreferences())
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

    fun resetAppInfoUpdated() =
        SharedPreferencesManager.put(KEY_INFO_UPDATED_VERSION, DEFAULT_INFO_UPDATED_VERSION)

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

    var uuidDebugEnabled: Boolean
        get() = LoggingExceptionHandler.runCatching(defaultValue = true) {
            SharedPreferencesManager.getBoolean(KEY_UUID_DEBUG_ENABLED, true)
        }
        set(value) {
            LoggingExceptionHandler.runCatching {
                SharedPreferencesManager.put(KEY_UUID_DEBUG_ENABLED, value)
            }
        }

    var requestPermissionCount: Int
        get() =
            LoggingExceptionHandler.runCatching(defaultValue = 0) {
                SharedPreferencesManager.getInt(KEY_REQUEST_PERMISSION_COUNT, 0)
            }
        set(value) {
            LoggingExceptionHandler.runCatching {
                SharedPreferencesManager.put(KEY_REQUEST_PERMISSION_COUNT, value)
            }
        }

    var userVisitCount: Int
        get() =
            LoggingExceptionHandler.runCatching(defaultValue = 0) {
                SharedPreferencesManager.getInt(KEY_USER_VISIT_COUNT, 0)
            }
        set(value) {
            LoggingExceptionHandler.runCatching {
                SharedPreferencesManager.put(KEY_USER_VISIT_COUNT, value)
            }
        }

    var shownInApps: String
        get() =
            LoggingExceptionHandler.runCatching("") {
                SharedPreferencesManager.getString(IN_APPS_METADATA, "") ?: ""
            }

        set(value) {
            LoggingExceptionHandler.runCatching {
                SharedPreferencesManager.put(IN_APPS_METADATA, value)
            }
        }

    var inAppConfigUpdatedTime: Long
        get() =
            LoggingExceptionHandler.runCatching(defaultValue = 0) {
                SharedPreferencesManager.getLong(KEY_CONFIG_UPDATE_DATE, 0)
            }
        set(value) {
            LoggingExceptionHandler.runCatching {
                SharedPreferencesManager.put(KEY_CONFIG_UPDATE_DATE, value)
            }
        }

    var versionCode: Int?
        get() = LoggingExceptionHandler.runCatching(defaultValue = null) {
            SharedPreferencesManager.getInt(KEY_SDK_VERSION_CODE)
        }
        set(value) {
            LoggingExceptionHandler.runCatching {
                SharedPreferencesManager.put(KEY_SDK_VERSION_CODE, value ?: Constants.SDK_VERSION_CODE)
            }
        }
}
