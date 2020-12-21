package com.mindbox.androidsdk

import com.orhanobut.hawk.Hawk

object Prefs {

    private const val KEY_ENDPOINT = "key_endpoint"

    var enteredEndpoint: String
        get() = Hawk.get(KEY_ENDPOINT, "")
        set(value) {
            Hawk.put(KEY_ENDPOINT, value)
        }
}