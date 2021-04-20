package cloud.mindbox.mobile_sdk

import cloud.mindbox.mobile_sdk.managers.SharedPreferencesManager
import io.paperdb.Paper
import io.paperdb.PaperDbException

internal fun removeConfiguration() {
    try {
        val configurationBook = Paper.book("mindbox_configuration_book")
        configurationBook.destroy()
    } catch (exception: PaperDbException) {
        exception.printStackTrace()
    }
}

internal fun removeEvents() {
    try {
        val configurationBook = Paper.book("mindbox_events_book")
        configurationBook.destroy()
    } catch (exception: PaperDbException) {
        exception.printStackTrace()
    }
}

fun clearPreferences() {
    SharedPreferencesManager.deleteAll()
}
