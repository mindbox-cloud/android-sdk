package com.mindbox.androidsdk

import com.orhanobut.hawk.Hawk

object Prefs {

    private const val KEY_ENDPOINT = "key_endpoint"
    private const val KEY_DOMAIN = "key_domain"

    var enteredEndpoint: String
        get() = Hawk.get(KEY_ENDPOINT, "")
        set(value) {
            Hawk.put(KEY_ENDPOINT, value)
        }

    var enteredDomain: String
        get() = Hawk.get(KEY_DOMAIN, "")
        set(value) {
            Hawk.put(KEY_DOMAIN, value)
        }
}