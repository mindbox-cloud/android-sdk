package cloud.mindbox.mobile_sdk.repository

import com.orhanobut.hawk.Hawk
import java.util.*

internal object MindboxPreferences {

    private const val KEY_IS_FIRST_INITIALIZATION = "key_is_first_initialization"
    private const val KEY_USER_ADID = "key_user_uuid"
    private const val KEY_INSTALLATION_ID = "key_installation_id"
    private const val KEY_FIREBASE_TOKEN = "key_firebase_token"
    private const val KEY_FIREBASE_TOKEN_SAVE_DATE = "key_firebase_token_save_date"
    private const val KEY_ENDPOINT = "key_endpoint"
    private const val KEY_DOMAIN = "key_domain"
    private const val KEY_PACKAGE_NAME = "key_package_name"
    private const val KEY_VERSION_NAME = "key_version_name"
    private const val KEY_VERSION_CODE = "key_version_code"

    var isFirstInitialize: Boolean
        get() = Hawk.get(KEY_IS_FIRST_INITIALIZATION, true)
        set(value) {
            Hawk.put(KEY_IS_FIRST_INITIALIZATION, value)
        }

    var deviceId: String?
        get() = Hawk.get(KEY_USER_ADID, null)
        set(value) {
            Hawk.put(KEY_USER_ADID, value)
        }

    var installationId: String?
        get() = Hawk.get(KEY_INSTALLATION_ID, null)
        set(value) {
            Hawk.put(KEY_INSTALLATION_ID, value)
        }

    var firebaseToken: String?
        get() = Hawk.get(KEY_FIREBASE_TOKEN, null)
        set(value) {
            Hawk.put(KEY_FIREBASE_TOKEN, value)
            firebaseTokenSaveDate = Date().toString()
        }

    var firebaseTokenSaveDate: String
        get() = Hawk.get(KEY_FIREBASE_TOKEN_SAVE_DATE, "")
        set(value) {
            Hawk.put(KEY_FIREBASE_TOKEN_SAVE_DATE, value)
        }

    var endpoint: String
        get() = Hawk.get(KEY_ENDPOINT, "")
        set(value) {
            Hawk.put(KEY_ENDPOINT, value)
        }

    var domain: String
        get() = Hawk.get(KEY_DOMAIN, "")
        set(value) {
            Hawk.put(KEY_DOMAIN, value)
        }

    var packageName: String
        get() = Hawk.get(KEY_PACKAGE_NAME, "")
        set(value) {
            Hawk.put(KEY_PACKAGE_NAME, value)
        }

    var versionName: String
        get() = Hawk.get(KEY_VERSION_NAME, "")
        set(value) {
            Hawk.put(KEY_VERSION_NAME, value)
        }

    var versionCode: String
        get() = Hawk.get(KEY_VERSION_CODE, "")
        set(value) {
            Hawk.put(KEY_VERSION_CODE, value)
        }
}