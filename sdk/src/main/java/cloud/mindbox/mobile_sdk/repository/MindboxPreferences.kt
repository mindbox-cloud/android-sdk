package cloud.mindbox.mobile_sdk.repository

import cloud.mindbox.mobile_sdk.returnOnException
import com.orhanobut.hawk.Hawk
import java.util.*

internal object MindboxPreferences {

    private const val KEY_IS_FIRST_INITIALIZATION = "key_is_first_initialization"
    private const val KEY_DEVICE_UUID = "key_device_uuid"
    private const val KEY_FIREBASE_TOKEN = "key_firebase_token"
    private const val KEY_FIREBASE_TOKEN_SAVE_DATE = "key_firebase_token_save_date"
    private const val KEY_IS_NOTIFICATION_ENABLED = "key_is_notification_enabled"
    private const val KEY_HOST_APP_MANE =
        "key_host_app_name" //need for scheduling and stopping one-time background service
    private const val KEY_INFO_UPDATED_VERSION = "key_info_updated_version"
    private const val KEY_INSTANCE_ID = "key_instance_id"
    private const val DEFAULT_INFO_UPDATED_VERSION = 1

    var isFirstInitialize: Boolean
        get() = runCatching {
            return Hawk.get(KEY_IS_FIRST_INITIALIZATION, true)
        }.returnOnException { true }
        set(value) {
            runCatching {
                Hawk.put(KEY_IS_FIRST_INITIALIZATION, value)
            }.returnOnException { }
        }

    var deviceUuid: String
        get() = runCatching {
            return Hawk.get(KEY_DEVICE_UUID, "")
        }.returnOnException { "" }
        set(value) {
            runCatching {
                Hawk.put(KEY_DEVICE_UUID, value)
            }.returnOnException { }
        }

    var firebaseToken: String?
        get() = runCatching {
            return Hawk.get(KEY_FIREBASE_TOKEN, null)
        }.returnOnException { null }
        set(value) {
            runCatching {
                Hawk.put(KEY_FIREBASE_TOKEN, value)
                firebaseTokenSaveDate = Date().toString()
            }.returnOnException { }
        }

    var firebaseTokenSaveDate: String
        get() = runCatching {
            return Hawk.get(KEY_FIREBASE_TOKEN_SAVE_DATE, "")
        }.returnOnException { "" }
        set(value) {
            runCatching {
                Hawk.put(KEY_FIREBASE_TOKEN_SAVE_DATE, value)
            }.returnOnException { }
        }

    var isNotificationEnabled: Boolean
        get() = runCatching {
            return Hawk.get(KEY_IS_NOTIFICATION_ENABLED, true)
        }.returnOnException { true }
        set(value) {
            runCatching {
                Hawk.put(KEY_IS_NOTIFICATION_ENABLED, value)
            }.returnOnException { }
        }

    var hostAppName: String
        get() = runCatching {
            return Hawk.get(KEY_HOST_APP_MANE, "")
        }.returnOnException { "" }
        set(value) {
            runCatching {
                Hawk.put(KEY_HOST_APP_MANE, value)
            }.returnOnException { }
        }

    val infoUpdatedVersion: Int
        @Synchronized get() = runCatching {
            val version = Hawk.get(KEY_INFO_UPDATED_VERSION, DEFAULT_INFO_UPDATED_VERSION)
            Hawk.put(KEY_INFO_UPDATED_VERSION, version + 1)
            return version
        }.returnOnException { DEFAULT_INFO_UPDATED_VERSION }

    var instanceId: String
        get() = runCatching {
            return Hawk.get(KEY_INSTANCE_ID, "")
        }.returnOnException { "" }
        set(value) {
            runCatching {
                Hawk.put(KEY_INSTANCE_ID, value)
            }.returnOnException { }
        }

}
