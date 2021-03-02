package cloud.mindbox.mobile_sdk.repository

import cloud.mindbox.mobile_sdk.returnOnException
import com.orhanobut.hawk.Hawk
import java.util.*

internal object MindboxPreferences {

    private const val KEY_IS_FIRST_INITIALIZATION = "key_is_first_initialization"
    private const val KEY_USER_ADID = "key_user_uuid"
    private const val KEY_INSTALLATION_ID = "key_installation_id"
    private const val KEY_FIREBASE_TOKEN = "key_firebase_token"
    private const val KEY_FIREBASE_TOKEN_SAVE_DATE = "key_firebase_token_save_date"
    private const val KEY_IS_NOTIFICATION_ENABLED = "key_is_notification_enabled"
    private const val KEY_HOST_APP_MANE =
        "key_host_app_name" //need for scheduling and stopping one-time background service

    var isFirstInitialize: Boolean
        get() = runCatching {
            Hawk.get(KEY_IS_FIRST_INITIALIZATION, true)
        }.returnOnException { true }
        set(value) {
            runCatching {
                Hawk.put(KEY_IS_FIRST_INITIALIZATION, value)
            }.returnOnException { }
        }

    var deviceUuid: String?
        get() = runCatching {
            Hawk.get(KEY_USER_ADID, null)
        }.returnOnException { null }
        set(value) {
            runCatching {
                Hawk.put(KEY_USER_ADID, value)
            }.returnOnException { }
        }

    var installationId: String?
        get() = runCatching {
            Hawk.get(KEY_INSTALLATION_ID, null)
        }.returnOnException { null }
        set(value) {
            runCatching {
                Hawk.put(KEY_INSTALLATION_ID, value)
            }.returnOnException { }
        }

    var firebaseToken: String?
        get() = runCatching {
            Hawk.get(KEY_FIREBASE_TOKEN, null)
        }.returnOnException { null }
        set(value) {
            runCatching {
                Hawk.put(KEY_FIREBASE_TOKEN, value)
                firebaseTokenSaveDate = Date().toString()
            }.returnOnException { }
        }

    var firebaseTokenSaveDate: String
        get() = runCatching {
            Hawk.get(KEY_FIREBASE_TOKEN_SAVE_DATE, "")
        }.returnOnException { "" }
        set(value) {
            runCatching {
                Hawk.put(KEY_FIREBASE_TOKEN_SAVE_DATE, value)
            }.returnOnException { }
        }

    var isNotificationEnabled: Boolean
        get() = runCatching {
            Hawk.get(KEY_IS_NOTIFICATION_ENABLED, true)
        }.returnOnException { true }
        set(value) {
            runCatching {
                Hawk.put(KEY_IS_NOTIFICATION_ENABLED, value)
            }.returnOnException { }
        }

    var hostAppName: String
        get() = runCatching {
            Hawk.get(KEY_HOST_APP_MANE, "")
        }.returnOnException { "" }
        set(value) {
            runCatching {
                Hawk.put(KEY_HOST_APP_MANE, value)
            }.returnOnException { }
        }
}