package cloud.mindbox.mobile_sdk

import com.orhanobut.hawk.Hawk

internal object MindboxPreferences {

    private const val KEY_IS_FIRST_INITIALIZATION = "key_is_first_initialization"
    private const val KEY_USER_ADID = "key_user_uuid"
    private const val KEY_INSTALLATION_ID = "key_installation_id"
    private const val KEY_FIREBASE_TOKEN = "key_firebase_token"

    var isFirstInitialize: Boolean
        get() = Hawk.get(KEY_IS_FIRST_INITIALIZATION, true)
        set(value) {
            Hawk.put(KEY_IS_FIRST_INITIALIZATION, value)
        }

    var userAdid: String?
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
        }
}