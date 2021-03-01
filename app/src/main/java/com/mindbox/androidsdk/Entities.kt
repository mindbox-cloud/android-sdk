package com.mindbox.androidsdk

data class InitializeData(
    val domain: String,
    val endpoint: String,
    val deviceId: String,
    val installId: String,
    val subscribe: Boolean
)