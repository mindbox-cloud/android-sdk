package com.mindbox.example

import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.inapp.presentation.callbacks.EmptyInAppCallback

fun chooseInappCallback(selectedInappCallback: RegisterInappCallback) {
    when (selectedInappCallback) {
        //by default apply ComposableInAppCallback
        RegisterInappCallback.DEFAULT -> {}
        RegisterInappCallback.CUSTOM ->
            Mindbox.registerInAppCallback(CustomInAppCallback)

        RegisterInappCallback.CHOOSE_MINDBOX_CALLBACK -> Mindbox.registerInAppCallback(
            EmptyInAppCallback()
        )
    }
}


