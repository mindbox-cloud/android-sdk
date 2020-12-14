package cloud.mindbox.mobile_sdk

import com.orhanobut.hawk.Hawk

internal object MindboxPreferences {

    private const val KEY_USER_ADID = "key_user_uuid"

    var userAdid: String?
        get() =  Hawk.get(KEY_USER_ADID)
        set(value) {
            Hawk.put(KEY_USER_ADID, value)
        }
}